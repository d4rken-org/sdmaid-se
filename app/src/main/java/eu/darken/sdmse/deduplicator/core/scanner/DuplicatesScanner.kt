package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.BuildConfig
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.endsWith
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumSleuth
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashSleuth
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.types.match
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider


class DuplicatesScanner @Inject constructor(
    private val checksumSleuthProvider: Provider<ChecksumSleuth>,
    private val pHashSleuthProvider: Provider<PHashSleuth>,
    private val exclusionManager: ExclusionManager,
    private val areaManager: DataAreaManager,
    private val dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
    private val commonFilesCheck: CommonFilesCheck,
    private val fileForensics: FileForensics,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private suspend fun defaultSearchFlow(): Flow<APathLookup<*>> {
        val currentAreas = areaManager.currentAreas()
        log(TAG) { "Current areas are: $currentAreas" }

        val targetAreas = run {
            val targetAreaTypes = setOf(
                DataArea.Type.SDCARD,
                DataArea.Type.PUBLIC_DATA,
                DataArea.Type.PUBLIC_MEDIA,
                DataArea.Type.PORTABLE,
            )
            currentAreas.filter { targetAreaTypes.contains(it.type) }.toSet()
        }
        log(TAG) { "Target areas: $targetAreas" }

        val sdcardSkips = mutableSetOf(
            segs("Android", "data"),
            segs("Android", "media"),
            segs("Android", "obb"),
        )

        val globalSkips = mutableSetOf<Segments>()
        if (targetAreas.all { it.path.segments != segs("", "data", "data") }) {
            globalSkips.add(segs("", "data", "data"))
            globalSkips.add(segs("", "data", "user", "0"))
        }
        globalSkips.add(segs("", "data", "media", "0"))
        log(TAG) { "Global skip segments: $globalSkips" }

        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.DEDUPLICATOR)
        log(TAG) { "Deduplicator exclusions are: $exclusions" }

        return targetAreas.asFlow()
            .flatMapMerge(2) { area ->
                val filter: suspend (APathLookup<*>) -> Boolean = when (area.type) {
                    DataArea.Type.SDCARD -> filter@{ toCheck: APathLookup<*> ->
                        if (sdcardSkips.any { toCheck.segments.endsWith(it) }) return@filter false
                        exclusions.none { it.match(toCheck) }
                    }

                    else -> filter@{ toCheck: APathLookup<*> ->
                        if (globalSkips.any { toCheck.segments.startsWith(it) }) {
                            log(TAG, WARN) { "Skipping: $toCheck" }
                            return@filter false
                        }
                        exclusions.none { it.match(toCheck) }
                    }
                }
                area.path.walk(
                    gatewaySwitch,
                    options = APathGateway.WalkOptions(
                        onFilter = filter
                    )
                )
            }
    }

    private suspend fun customPathSearchFlow(paths: Set<APath>): Flow<APathLookup<*>> {
        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.DEDUPLICATOR)
        log(TAG) { "Deduplicator exclusions are: $exclusions" }

        val allowedAreas = setOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_MEDIA,
            DataArea.Type.PORTABLE,
        )

        fileForensics.useRes {
            val testFileName = "${BuildConfig.PACKAGENAME}-testfile-${UUID.randomUUID()}"
            paths.forEach { path ->
                val testPath = path.child(testFileName)
                val area = fileForensics.identifyArea(testPath)
                if (allowedAreas.contains(area?.type)) {
                    log(TAG) { "Valid search area ${area?.type} -> $testPath" }
                } else {
                    throw IllegalArgumentException("Unsupported area for $testPath: $area")
                }
            }
        }

        return paths.asFlow()
            .flatMapMerge(2) { path ->
                val filter: suspend (APathLookup<*>) -> Boolean = filter@{ toCheck: APathLookup<*> ->
                    exclusions.none { it.match(toCheck) }
                }
                path.walk(
                    gatewaySwitch,
                    options = APathGateway.WalkOptions(
                        onFilter = filter
                    )
                )
            }
    }

    data class Options(
        val paths: Set<APath>,
        val minimumSize: Long,
        val skipUncommon: Boolean,
        val useSleuthChecksum: Boolean,
        val useSleuthPHash: Boolean,
    )

    suspend fun scan(options: Options): Set<Duplicate.Cluster> {
        log(TAG) { "scan($options)" }

        val searchPaths = if (options.paths.isEmpty()) {
            defaultSearchFlow()
        } else {
            customPathSearchFlow(options.paths)
        }

        val searchFlow = searchPaths
            .flowOn(dispatcherProvider.IO)
            .buffer(1024)
            .filter {
                if (!it.isFile) return@filter false
                val isGoodSize = it.size >= options.minimumSize
                val isGoodType = (!options.skipUncommon || commonFilesCheck.isCommon(it))
                if (Bugs.isDebug) log(TAG) { "goodSize=$isGoodSize, goodType=$isGoodType <-> $it" }
                isGoodSize && isGoodType
            }

        val cksGroups: Set<ChecksumDuplicate.Group> = if (options.useSleuthChecksum) {
            log(TAG) { "ChecksumSleuth is enabled" }
            checksumSleuthProvider.get().withProgress(this) { investigate(searchFlow) }
        } else {
            log(TAG) { "ChecksumSleuth is disabled" }
            emptySet()
        }

        val phGroups = if (options.useSleuthPHash) {
            log(TAG) { "PHashSleuth is enabled" }
            pHashSleuthProvider.get().withProgress(this) { investigate(searchFlow) }
        } else {
            log(TAG) { "PHashSleuth is disabled" }
            emptySet()
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        val overlaps = mutableMapOf<ChecksumDuplicate.Group, Set<PHashDuplicate.Group>>()
        val uniques = mutableSetOf<PHashDuplicate.Group>()

        // Sort phash results, which need to be grouped with others, and which are unique?
        phGroups.forEach { phGrp ->
            val phashPaths = phGrp.duplicates.map { it.path }.toSet()

            val cksOverlaps = cksGroups.filter { cksGrp ->
                cksGrp.duplicates.any { phashPaths.contains(it.path) }
            }

            when {
                cksOverlaps.isNotEmpty() -> {
                    cksOverlaps.forEach { overlaps[it] = (overlaps[it] ?: emptySet()).plus(phGrp) }
                }

                else -> {
                    uniques.add(phGrp)
                }
            }
        }

        val clusters = mutableSetOf<Duplicate.Cluster>()

        val cksCoveredPaths = cksGroups
            .map { it.duplicates }
            .flatten()
            .map { it.path }
            .toSet()

        cksGroups
            .map { cksGrp ->
                val overlapping = overlaps.remove(cksGrp)
                log(TAG, VERBOSE) { "${overlapping?.size} groups overlap with ChecksumGroup $cksGrp" }

                if (Bugs.isTrace) overlapping?.forEachIndexed { index, group -> log(TAG, VERBOSE) { "#$index $group" } }


                val grps = mutableSetOf<Duplicate.Group>(cksGrp)

                overlapping
                    ?.map { phGrp ->
                        // If some dupes are already covered by checksum matches, then they take precedence
                        // A similarity match that already has a checksum match, will not be shown as similarity match
                        val uniquePhDupes = phGrp.duplicates
                            .filter { phDupe -> !cksCoveredPaths.contains(phDupe.path) }
                            .toSet()
                        phGrp.copy(duplicates = uniquePhDupes)
                    }
                    ?.filter { it.duplicates.isNotEmpty() }
                    ?.let { grps.addAll(it) }

                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                    groups = grps
                )
            }
            .run { clusters.addAll(this) }

        uniques
            .map { phGrp ->
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Unique PHGroup: $phGrp" }
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                    groups = setOf(phGrp)
                )
            }
            .run { clusters.addAll(this) }

        if (Bugs.isTrace) {
            clusters.forEach { c ->
                log(
                    TAG,
                    VERBOSE
                ) { "performScan(): Cluster ${c.identifier}: ${c.groups.size} groups, ${c.count} dupes" }
                c.groups.forEach { g ->
                    log(TAG, VERBOSE) { "performScan():  Group ${g.identifier}: ${g.duplicates.size} dupes" }
                    g.duplicates.forEach { d ->
                        log(TAG, VERBOSE) { "performScan():   Duplicate: $d" }
                    }
                }
            }
        }
        return clusters
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Scanner")
    }
}
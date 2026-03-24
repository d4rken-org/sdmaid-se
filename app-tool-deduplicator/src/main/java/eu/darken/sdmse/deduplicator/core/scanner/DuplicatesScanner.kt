package eu.darken.sdmse.deduplicator.core.scanner

import eu.darken.sdmse.common.BuildConfigWrap
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
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumSleuth
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.media.MediaSleuth
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
import kotlinx.coroutines.flow.toSet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider


class DuplicatesScanner @Inject constructor(
    private val checksumSleuthProvider: Provider<ChecksumSleuth>,
    private val pHashSleuthProvider: Provider<PHashSleuth>,
    private val mediaHashSleuthProvider: Provider<MediaSleuth>,
    private val exclusionManager: ExclusionManager,
    private val areaManager: DataAreaManager,
    private val dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
    private val commonFilesCheck: CommonFilesCheck,
    private val fileForensics: FileForensics,
    private val arbiter: DuplicatesArbiter,
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
            val testFileName = "${BuildConfigWrap.APPLICATION_ID}-testfile-${UUID.randomUUID()}"
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
        val useSleuthMedia: Boolean,
    )

    suspend fun scan(options: Options): Set<Duplicate.Cluster> {
        log(TAG) { "scan($options)" }

        val searchPaths = if (options.paths.isEmpty()) {
            defaultSearchFlow()
        } else {
            customPathSearchFlow(options.paths)
        }

        // Materialize once to avoid redundant filesystem walks per sleuth
        val allCandidates = searchPaths
            .flowOn(dispatcherProvider.IO)
            .buffer(1024)
            .filter {
                if (!it.isFile) return@filter false
                val isGoodSize = it.size >= options.minimumSize
                val isGoodType = (!options.skipUncommon || commonFilesCheck.isCommon(it))
                if (Bugs.isDebug) log(TAG, VERBOSE) { "goodSize=$isGoodSize, goodType=$isGoodType <-> $it" }
                isGoodSize && isGoodType
            }
            .toSet()

        log(TAG) { "Collected ${allCandidates.size} candidates" }

        val candidateFlow = allCandidates.asFlow()

        val cksGroups: Set<ChecksumDuplicate.Group> = if (options.useSleuthChecksum) {
            log(TAG) { "ChecksumSleuth is enabled" }
            checksumSleuthProvider.get().withProgress(this) { investigate(candidateFlow) }
        } else {
            log(TAG) { "ChecksumSleuth is disabled" }
            emptySet()
        }

        val phGroups: Set<Duplicate.Group> = if (options.useSleuthPHash) {
            log(TAG) { "PHashSleuth is enabled" }
            pHashSleuthProvider.get().withProgress(this) { investigate(candidateFlow) }
        } else {
            log(TAG) { "PHashSleuth is disabled" }
            emptySet()
        }

        val mhGroups: Set<Duplicate.Group> = if (options.useSleuthMedia) {
            log(TAG) { "MediaSleuth is enabled" }
            mediaHashSleuthProvider.get().withProgress(this) { investigate(candidateFlow) }
        } else {
            log(TAG) { "MediaSleuth is disabled" }
            emptySet()
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        // Generic overlap merge: checksum groups take precedence over all similarity groups
        val similarityGroups = phGroups + mhGroups
        val overlaps = mutableMapOf<ChecksumDuplicate.Group, MutableSet<Duplicate.Group>>()
        val uniques = mutableSetOf<Duplicate.Group>()

        similarityGroups.forEach { simGrp ->
            val simPaths = simGrp.duplicates.map { it.path }.toSet()

            val cksOverlaps = cksGroups.filter { cksGrp ->
                cksGrp.duplicates.any { simPaths.contains(it.path) }
            }

            if (cksOverlaps.isNotEmpty()) {
                cksOverlaps.forEach {
                    overlaps.getOrPut(it) { mutableSetOf() }.add(simGrp)
                }
            } else {
                uniques.add(simGrp)
            }
        }

        val clusters = mutableSetOf<Duplicate.Cluster>()

        val cksCoveredPaths = cksGroups
            .flatMap { it.duplicates }
            .map { it.path }
            .toSet()

        cksGroups
            .map { cksGrp ->
                val overlapping = overlaps.remove(cksGrp)
                log(TAG, VERBOSE) { "${overlapping?.size} groups overlap with ChecksumGroup $cksGrp" }

                if (Bugs.isTrace) overlapping?.forEachIndexed { index, group -> log(TAG, VERBOSE) { "#$index $group" } }

                val grps = mutableSetOf<Duplicate.Group>(cksGrp)

                overlapping
                    ?.mapNotNull { simGrp -> simGrp.stripCoveredPaths(cksCoveredPaths) }
                    ?.let { grps.addAll(it) }

                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                    groups = grps,
                )
            }
            .run { clusters.addAll(this) }

        uniques
            .map { simGrp ->
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Unique similarity group: $simGrp" }
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id(UUID.randomUUID().toString()),
                    groups = setOf(simGrp),
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
        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressCount(Progress.Count.Percent(0, clusters.size))

        val strategy = arbiter.getStrategy()
        var clusterIndex = 0
        val clustersWithKeepers = clusters.map { cluster ->
            updateProgressCount(Progress.Count.Percent(++clusterIndex, clusters.size))
            updateProgressSecondary(cluster.previewFile.userReadablePath)
            val groupsWithKeepers = cluster.groups.map { group ->
                if (group.duplicates.size < 2) return@map group
                val keeper = arbiter.decideDuplicates(group.duplicates, strategy).first
                when (group) {
                    is ChecksumDuplicate.Group -> group.copy(keeperIdentifier = keeper.identifier)
                    is PHashDuplicate.Group -> group.copy(keeperIdentifier = keeper.identifier)
                    is MediaDuplicate.Group -> group.copy(keeperIdentifier = keeper.identifier)
                    else -> group
                }
            }.toSet()

            val favoriteGroupId = if (groupsWithKeepers.size >= 2) {
                arbiter.decideGroups(groupsWithKeepers, strategy).first.identifier
            } else {
                groupsWithKeepers.firstOrNull()?.identifier
            }

            cluster.copy(groups = groupsWithKeepers, favoriteGroupIdentifier = favoriteGroupId)
        }.toSet()

        return clustersWithKeepers
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Scanner")
    }
}

/**
 * Strips duplicates whose paths are already covered by checksum groups.
 * Returns null if no duplicates remain after stripping.
 */
internal fun Duplicate.Group.stripCoveredPaths(coveredPaths: Set<APath>): Duplicate.Group? {
    val stripped = when (this) {
        is PHashDuplicate.Group -> copy(duplicates = duplicates.filter { !coveredPaths.contains(it.path) }.toSet())
        is MediaDuplicate.Group -> copy(duplicates = duplicates.filter { !coveredPaths.contains(it.path) }.toSet())
        else -> return null
    }
    return stripped.takeIf { it.duplicates.isNotEmpty() }
}
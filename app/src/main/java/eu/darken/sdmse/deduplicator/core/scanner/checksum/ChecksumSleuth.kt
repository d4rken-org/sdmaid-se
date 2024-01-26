package eu.darken.sdmse.deduplicator.core.scanner.checksum

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
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
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.common.hashing.hash
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.CommonFilesCheck
import eu.darken.sdmse.deduplicator.core.scanner.Sleuth
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.types.match
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import javax.inject.Inject
import javax.inject.Provider

class ChecksumSleuth @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val settings: DeduplicatorSettings,
    private val commonFilesCheck: CommonFilesCheck,
) : Sleuth {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    override suspend fun investigate(): Set<ChecksumDuplicate.Group> {
        log(TAG) { "investigate():..." }
        updateProgressPrimary(R.string.deduplicator_detection_method_checksum_title.toCaString())
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading)

        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.DEDUPLICATOR)

        val currentAreas = areaManager.currentAreas()

        val targetAreas = run {
            val targetAreaTypes = setOf(
                DataArea.Type.SDCARD,
                DataArea.Type.PUBLIC_DATA,
                DataArea.Type.PUBLIC_MEDIA,
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

        val minSize = settings.minSizeBytes.value()
        val skipUncommon = settings.skipUncommon.value()

        val suspects = mutableSetOf<APathLookup<*>>()

        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_searching)

        gatewaySwitch.useRes {
            targetAreas
                .asFlow()
                .flowOn(dispatcherProvider.IO)
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
                .buffer(1024)
                .filter {
                    it.isFile && it.size >= minSize && (!skipUncommon || commonFilesCheck.isCommon(it))
                }
                .collect { item -> suspects.add(item) }
        }

        val sizeBuckets: Map<Long, List<APathLookup<*>>> = suspects
            .groupBy { it.size }
            .filterValues { it.size >= 2 }
        log(TAG) { "${sizeBuckets.size} size buckets of 2 or more items" }

        updateProgressSecondary(R.string.deduplicator_progress_comparing_files)
        updateProgressCount(Progress.Count.Percent(sizeBuckets.values.sumOf { it.size }))

        val hashStart = System.currentTimeMillis()
        val hashBuckets = sizeBuckets.values
            .asFlow()
            .flatMapMerge { items ->
                flow {
                    val checksummedGroup = items.map { item ->
                        val start = System.currentTimeMillis()

                        val hash = try {
                            gatewaySwitch.read(item.lookedUp).hash(Hasher.Type.SHA256)
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Failed to read $item: ${e.asLog()}" }
                            return@flow
                        }
                        val hexHash = hash.format()

                        val stop = System.currentTimeMillis()
                        log(TAG, VERBOSE) {
                            "Checksum: $hexHash - ${String.format("%4d", stop - start)}ms - ${item.path}"
                        }

                        increaseProgress()
                        Triple(item, hash, hexHash)
                    }
                    emit(checksummedGroup)
                }
            }
            .toList()
            .flatten()
            .groupBy { hexHash -> hexHash.third }
            .filter { it.value.size >= 2 }

        val hashStop = System.currentTimeMillis()
        log(TAG) { "Checksum investigation took ${(hashStop - hashStart)}ms (${DEFAULT_CONCURRENCY})" }

        log(TAG) { "${hashBuckets.size} hash buckets of 2 or more items" }

        val duplicates = hashBuckets.values
            .map { dupes: List<Triple<APathLookup<*>, Hasher.Result, String>> ->
                val hexHash = dupes.first().third
                ChecksumDuplicate.Group(
                    identifier = Duplicate.Group.Id(hexHash),
                    duplicates = dupes
                        .map { (item, hash) ->
                            ChecksumDuplicate(
                                lookup = item,
                                hash = hash,
                            )
                        }
                        .toSet()
                )
            }

        return duplicates.toSet()
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: DeduplicatorSettings,
        private val sleuthProvider: Provider<ChecksumSleuth>
    ) : Sleuth.Factory {
        override suspend fun isEnabled(): Boolean = settings.isSleuthChecksumEnabled.value()
        override suspend fun create(): ChecksumSleuth = sleuthProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): Sleuth.Factory
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Sleuth", "Hash")
    }
}
package eu.darken.sdmse.deduplicator.core.scanner.checksum

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.common.hashing.hash
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.Sleuth
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import javax.inject.Inject

class ChecksumSleuth @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
) : Sleuth {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    override suspend fun investigate(searchFlow: Flow<APathLookup<*>>): Set<ChecksumDuplicate.Group> {
        log(TAG) { "investigate($searchFlow):..." }
        updateProgressPrimary(R.string.deduplicator_detection_method_checksum_title.toCaString())
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_searching)

        val suspects = searchFlow.toSet()

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
                            gatewaySwitch.file(item.lookedUp, readWrite = false).source().hash(Hasher.Type.SHA256)
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

    companion object {
        private val TAG = logTag("Deduplicator", "Sleuth", "Hash")
    }
}
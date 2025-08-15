package eu.darken.sdmse.deduplicator.core.scanner.phash

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coil.BitmapFetcher
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.CommonFilesCheck
import eu.darken.sdmse.deduplicator.core.scanner.Sleuth
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.isActive
import java.io.IOException
import java.util.LinkedList
import java.util.UUID
import javax.inject.Inject

class PHashSleuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val commonFilesCheck: CommonFilesCheck,
) : Sleuth {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    override suspend fun investigate(searchFlow: Flow<APathLookup<*>>): Set<PHashDuplicate.Group> {
        log(TAG) { "investigate($searchFlow):..." }
        updateProgressPrimary(R.string.deduplicator_detection_method_phash_title.toCaString())
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_searching)

        val suspects = searchFlow
            .filter { commonFilesCheck.isImage(it) }
            .toSet()

        updateProgressSecondary(R.string.deduplicator_progress_comparing_files)
        updateProgressCount(Progress.Count.Percent(suspects.size))

        val hashStart = System.currentTimeMillis()

        val hashedItems: Map<APathLookup<*>, PHasher.Result> = suspects
            .asFlow()
            .flowOn(dispatcherProvider.IO)
            .flatMapMerge { item ->
                flow {
                    val start = System.currentTimeMillis()

                    val hash = try {
                        item.calculatePHash(context)
                    } catch (e: IOException) {
                        log(TAG, WARN) { "Failed to determine phash for $item: $e" }
                        null
                    }

                    if (Bugs.isTrace) {
                        val stop = System.currentTimeMillis()
                        log(TAG, VERBOSE) {
                            "PHash ${hash?.format()} took ${String.format("%4d", stop - start)}ms - ${item.path}"
                        }
                    }

                    increaseProgress()
                    emit(if (hash != null) item to hash else null)
                }
            }
            .filterNotNull()
            .toList()
            .toMap()

        val requiredSim = 0.95f
        val remainingItems = LinkedList(hashedItems.keys)
        val hashBuckets = mutableSetOf<Set<Pair<APathLookup<*>, Double>>>()

        updateProgressCount(Progress.Count.Percent(remainingItems.size))

        while (currentCoroutineContext().isActive && remainingItems.isNotEmpty()) {
            val target = remainingItems.removeFirst()
            val targetHash = hashedItems[target]!!

            val others = remainingItems
                .map { it to targetHash.similarityTo(hashedItems[it]!!) }
                .onEach { (other, sim) ->
                    if (Bugs.isTrace || sim > requiredSim) {
                        log(TAG, VERBOSE) { "${String.format("%.2f%%", sim * 100)} : $target <-> $other" }
                    }
                }
                .filter { it.second > requiredSim }

            remainingItems.removeAll(others.map { it.first }.toSet())

            if (others.isEmpty()) continue

            // We group the target with others and use the average distance as it's distance
            val targetWithOthers = others.plus(target to others.map { it.second }.average()).toSet()
            hashBuckets.add(targetWithOthers)
            increaseProgress(remainingItems.size)
        }

        val hashStop = System.currentTimeMillis()
        log(TAG) { "PHash investigation took ${(hashStop - hashStart)}ms (${DEFAULT_CONCURRENCY})" }

        return hashBuckets.map { items: Set<Pair<APathLookup<*>, Double>> ->
            PHashDuplicate.Group(
                identifier = Duplicate.Group.Id(UUID.randomUUID().toString()),
                duplicates = items.map { (item, similarity) ->
                    PHashDuplicate(
                        lookup = item,
                        hash = hashedItems[item]!!,
                        similarity = similarity,
                    )
                }.toSet()
            )
        }.toSet()
    }

    private suspend fun APathLookup<*>.calculatePHash(context: Context): PHasher.Result {
        val request = ImageRequest.Builder(context).apply {
            data(BitmapFetcher.Request(this@calculatePHash))
            // Hardware backed bitmaps don't support direct pixel access
            allowHardware(false)
            size(1024)
        }.build()

        val result = context.imageLoader.execute(request)

        if (result !is SuccessResult) {
            throw IOException("Failed to load bitmap for $this: $result")
        }

        return PHasher().calc(result.drawable.toBitmap())
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Sleuth", "PHash")
    }
}
package eu.darken.sdmse.deduplicator.core.scanner.phash

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.extension
import eu.darken.sdmse.common.files.inputStream
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.deduplicator.R
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class PHashSleuth @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
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
            .filter { it.extension?.lowercase() != "svg" }
            .toSet()

        updateProgressSecondary(R.string.deduplicator_progress_comparing_files)
        updateProgressCount(Progress.Count.Percent(suspects.size))

        val hashStart = System.currentTimeMillis()
        val totalDecodeMs = AtomicLong(0)
        val totalHashMs = AtomicLong(0)
        val processedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val processSemaphore = Semaphore(MAX_CONCURRENT_OPS)
        val pHasher = PHasher()

        val hashedItems: Map<APathLookup<*>, PHasher.Result> = suspects
            .asFlow()
            .flatMapMerge { item ->
                flow {
                    val hash = try {
                        processSemaphore.withPermit {
                            val decodeStart = System.currentTimeMillis()
                            val bitmap = item.loadBitmap()
                            val decodeMs = System.currentTimeMillis() - decodeStart
                            totalDecodeMs.addAndGet(decodeMs)

                            try {
                                val calcStart = System.currentTimeMillis()
                                val result = pHasher.calc(bitmap)
                                val calcMs = System.currentTimeMillis() - calcStart
                                totalHashMs.addAndGet(calcMs)
                                processedCount.incrementAndGet()

                                log(TAG, VERBOSE) {
                                    "PHash decode=${decodeMs}ms hash=${calcMs}ms" +
                                            " acVar=${String.format("%.1f", result.acVariance)}" +
                                            " size=${item.size / 1024}KB - ${item.path}"
                                }
                                result
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    } catch (e: IOException) {
                        log(TAG, WARN) { "Failed to load bitmap for $item: $e" }
                        failedCount.incrementAndGet()
                        null
                    } catch (e: OutOfMemoryError) {
                        log(TAG, WARN) { "OOM loading bitmap for $item: $e" }
                        failedCount.incrementAndGet()
                        null
                    }

                    increaseProgress()
                    emit(if (hash != null) item to hash else null)
                }
            }
            .flowOn(dispatcherProvider.IO)
            .filterNotNull()
            .toList()
            .toMap()

        val filteredItems = hashedItems.filter { (item, result) ->
            val lowComplexity = result.acVariance < MIN_AC_VARIANCE
            if (lowComplexity) {
                log(TAG, VERBOSE) {
                    "Skipping low-complexity image (acVar=${String.format("%.2f", result.acVariance)}): ${item.path}"
                }
            }
            !lowComplexity
        }

        val skippedCount = hashedItems.size - filteredItems.size
        if (skippedCount > 0) {
            log(TAG, INFO) { "PHash: skipped $skippedCount low-complexity images (acVariance < $MIN_AC_VARIANCE)" }
        }

        val compareStart = System.currentTimeMillis()
        val requiredSim = 0.95f
        val remainingItems = LinkedList(filteredItems.keys)
        val hashBuckets = mutableSetOf<Set<Pair<APathLookup<*>, Double>>>()

        updateProgressCount(Progress.Count.Percent(remainingItems.size))

        while (currentCoroutineContext().isActive && remainingItems.isNotEmpty()) {
            val target = remainingItems.removeFirst()
            val targetHash = filteredItems[target]!!

            val others = remainingItems
                .map { it to targetHash.similarityTo(filteredItems[it]!!) }
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
        val wallMs = hashStop - hashStart
        val processed = processedCount.get()
        val failed = failedCount.get()
        log(TAG, INFO) {
            "PHash: ${processed} images in ${wallMs}ms (${if (processed > 0) wallMs / processed else 0}ms/img)," +
                    " decode=${totalDecodeMs.get()}ms hash=${totalHashMs.get()}ms," +
                    " failed=$failed, concurrency=$DEFAULT_CONCURRENCY"
        }

        val compareMs = System.currentTimeMillis() - compareStart
        log(
            TAG,
            INFO
        ) { "PHash comparison: ${filteredItems.size} items in ${compareMs}ms, found ${hashBuckets.size} groups" }

        return hashBuckets.map { items: Set<Pair<APathLookup<*>, Double>> ->
            PHashDuplicate.Group(
                identifier = Duplicate.Group.Id(UUID.randomUUID().toString()),
                duplicates = items.map { (item, similarity) ->
                    PHashDuplicate(
                        lookup = item,
                        hash = filteredItems[item]!!,
                        similarity = similarity,
                    )
                }.toSet()
            )
        }.toSet()
    }

    private suspend fun APathLookup<*>.loadBitmap(): Bitmap {
        return gatewaySwitch.file(lookedUp, readWrite = false).use { handle ->
            // Pass 1: decode bounds only
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            handle.source().inputStream().use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                throw IOException("Failed to decode bounds for $this (${boundsOptions.outWidth}x${boundsOptions.outHeight})")
            }

            // Pass 2: decode with optimal inSampleSize targeting PHASH_SIZE
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = boundsOptions.calculateInSampleSize()
            }
            val rawBitmap = handle.source().inputStream().use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IOException("Failed to decode bitmap for $this")

            // Pass 3: read EXIF orientation and apply rotation
            val orientation = try {
                handle.source().inputStream().use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            } catch (e: IOException) {
                log(TAG, VERBOSE) { "Failed to read exif orientation (defaulting to NORMAL): $e" }
                ExifInterface.ORIENTATION_NORMAL
            }

            rawBitmap.applyExifOrientation(orientation)
        }
    }

    companion object {
        private const val MAX_CONCURRENT_OPS = 4

        // Minimum AC coefficient variance for a meaningful pHash comparison.
        // Images below this have near-uniform content where DCT hash bits are noise-dominated.
        private const val MIN_AC_VARIANCE = 100.0

        // Decode target: larger than the 64x64 PHash input to preserve detail during resize.
        // inSampleSize will decode to roughly this size, then PHashAlgorithm resizes to 64x64.
        private const val DECODE_TARGET_SIZE = 256

        private val TAG = logTag("Deduplicator", "Sleuth", "PHash")
    }
}

private fun BitmapFactory.Options.calculateInSampleSize(): Int {
    var inSampleSize = 1
    val targetSize = 256
    while (outWidth / (inSampleSize * 2) >= targetSize && outHeight / (inSampleSize * 2) >= targetSize) {
        inSampleSize *= 2
    }
    return inSampleSize
}

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { postRotate(270f); postScale(-1f, 1f) }
        else -> return this
    }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}

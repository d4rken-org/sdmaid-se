package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.SqueezerEligibility
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.ImageContentHasher
import eu.darken.sdmse.squeezer.core.scanner.LossyAuxDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException


class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCompressor: ImageCompressor,
    private val dispatcherProvider: DispatcherProvider,
    private val historyDatabase: CompressionHistoryDatabase,
    private val imageContentHasher: ImageContentHasher,
    private val fileTransaction: FileTransaction,
    private val lossyAuxDetector: LossyAuxDetector,
    private val settings: SqueezerSettings,
) : Progress.Host, Progress.Client {

    // Progress.Data()'s default primary is "Loading". withProgress() starts forwarding before it
    // calls action() and a StateFlow replays its current value to a new collector, so a bare
    // default would flash "Loading" before the pre-loop publish below lands.
    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Result(
        val success: Set<CompressibleImage>,
        val failed: Map<CompressibleImage, Throwable>,
        val savedSpace: Long,
        /**
         * Photos left untouched by the protection preflight guard (HDR/depth, Motion Photo,
         * oversized). Not "compressed" (so excluded from the processed count and affected paths)
         * but still consumed from the scan list.
         */
        val skippedGuarded: Set<CompressibleImage> = emptySet(),
    )

    /**
     * [itemOffset] / [itemTotal] carry the position of this batch inside a run that also processes
     * videos, so the published counter is "file 2 of 10" across both passes instead of restarting.
     */
    suspend fun process(
        targets: Set<CompressibleImage>,
        quality: Int,
        itemOffset: Int = 0,
        itemTotal: Int = targets.size,
    ): Result = withContext(dispatcherProvider.IO) {
        log(TAG) { "process(${targets.size} images, quality=$quality, offset=$itemOffset, total=$itemTotal)" }

        updateProgress {
            (it ?: Progress.Data()).copy(
                primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString(),
                secondary = CaString.EMPTY,
                // Keeps the item counter the caller already published for the previous phase,
                // otherwise the outer ring drops from "1 of 2" to spinning and then jumps back.
                count = Progress.Count.Counter(itemOffset, itemTotal),
                subCount = null,
            )
        }

        log(TAG, INFO) { "Processing ${targets.size} images" }

        val successful = mutableSetOf<CompressibleImage>()
        val failed = mutableMapOf<CompressibleImage, Throwable>()
        val skippedGuarded = mutableSetOf<CompressibleImage>()
        var totalSaved = 0L

        targets.forEachIndexed { index, image ->
            // One publish, not four: progress is exposed through throttleLatest(250), which
            // conflates. Separate mutations let a fast item show up with the new file name next to
            // the previous path and counter for up to a throttle window. There is no per-image
            // progress to report, so the sub-count just spins for the duration of the item.
            updateProgress {
                (it ?: Progress.Data()).copy(
                    primary = eu.darken.sdmse.common.R.string.general_progress_processing_x
                        .toCaString(image.path.name),
                    secondary = image.lookup.userReadablePath,
                    count = Progress.Count.Counter(itemOffset + index, itemTotal),
                    subCount = Progress.Count.Indeterminate(),
                )
            }

            // Authoritative protection guard. The scan already excludes these when the opt-in is
            // off, but re-check against the CURRENT settings here so a photo can't be flattened if
            // the user toggled a setting off after scanning. Mirrors the existing skip paths:
            // counted as handled, never compressed, no history written.
            val guardFile = (image.path as? LocalPath)?.let { File(it.path) }
            val guardReason = when {
                guardFile == null -> null
                !settings.includeLossyAuxImages.value() &&
                    lossyAuxDetector.hasLossyAux(guardFile, image.mimeType) -> "HDR/depth"
                !settings.includeMotionPhotos.value() &&
                    lossyAuxDetector.hasMotionVideo(guardFile, image.mimeType) -> "Motion Photo"
                !settings.includeOversizedImages.value() && image.willDownscale -> "oversized"
                else -> null
            }
            if (guardReason != null) {
                log(TAG, INFO) { "Skipped ${image.path} ($guardReason preserved)" }
                skippedGuarded.add(image)
                // Skipped photos advance the counter too, otherwise the ring stalls across them.
                updateProgress {
                    (it ?: Progress.Data()).copy(
                        count = Progress.Count.Counter(itemOffset + index + 1, itemTotal),
                        subCount = Progress.Count.Indeterminate(),
                    )
                }
                return@forEachIndexed
            }

            try {
                val outcome = processImage(image, quality)
                successful.add(image)

                if (outcome.replaced) {
                    totalSaved += outcome.savedBytes
                    try {
                        val identifier = imageContentHasher.computeHash(image.path)
                        historyDatabase.recordCompression(identifier.contentId)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to record compression for ${image.path}: ${e.message}" }
                    }
                    log(TAG, VERBOSE) { "Compressed ${image.path}: saved ${outcome.savedBytes} bytes" }
                } else if (outcome.savedBytes == 0L) {
                    // Actual re-encode ran and produced output >= original. Record so rescans
                    // don't burn cycles re-trying a file we already know won't shrink. Dry runs
                    // fall through to the else branch (they have savedBytes > 0 even though
                    // replaced = false). User's retry escape hatch is clearing history.
                    try {
                        val identifier = imageContentHasher.computeHash(image.path)
                        historyDatabase.recordNoSavings(identifier.contentId)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to record no-savings for ${image.path}: ${e.message}" }
                    }
                    log(TAG, INFO) { "Skipped ${image.path} (no savings, recorded)" }
                } else {
                    log(TAG, INFO) { "Skipped ${image.path} (dry-run)" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to compress ${image.path}: ${e.asLog()}" }
                failed[image] = e
            }

            // Every exit path (compressed, skipped, failed) advances the item counter here, so the
            // ring doesn't stall through FileTransaction's rename/verify and the history write.
            updateProgress {
                (it ?: Progress.Data()).copy(
                    count = Progress.Count.Counter(itemOffset + index + 1, itemTotal),
                    subCount = Progress.Count.Indeterminate(),
                )
            }
        }

        log(TAG, INFO) {
            "Processing complete: ${successful.size}/${targets.size} images, ${failed.size} failed, " +
                "${skippedGuarded.size} guard-preserved, saved $totalSaved bytes"
        }

        Result(
            success = successful,
            failed = failed,
            savedSpace = totalSaved,
            skippedGuarded = skippedGuarded,
        )
    }

    private suspend fun processImage(
        image: CompressibleImage,
        quality: Int,
    ): FileTransaction.Outcome {
        // TODO(gateway): bitmap decode/encode already stream via FileInputStream /
        // FileOutputStream, but (a) ExifPreserver works on raw file paths and (b) the
        // atomic swap in FileTransaction operates on java.io.File. MediaScanner pre-filters
        // accessibility at Mode.NORMAL — preflight here so state drift between scan and
        // process surfaces as a typed IO error instead of a crash in native EXIF code.
        val localPath = image.path as? LocalPath
            ?: throw IllegalArgumentException("Only local paths are supported: ${image.path}")

        val originalFile = File(localPath.path)

        val verdict = SqueezerEligibility.check(originalFile)
        if (verdict != SqueezerEligibility.Verdict.OK) {
            throw IOException("File no longer processable ($verdict): ${originalFile.path}")
        }

        val outcome = fileTransaction.replace(
            target = originalFile,
            dryRun = Bugs.isDryRun,
        ) { tempFile ->
            imageCompressor.compress(
                inputFile = originalFile,
                outputFile = tempFile,
                mimeType = image.mimeType,
                quality = quality,
                writeExifMarker = settings.writeExifMarker.value(),
            )
        }

        if (outcome.replaced) {
            notifyMediaScanner(originalFile)
        }

        return outcome
    }

    private fun notifyMediaScanner(file: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null
        )
    }

    companion object {
        private val TAG = logTag("Squeezer", "Processor")
    }
}

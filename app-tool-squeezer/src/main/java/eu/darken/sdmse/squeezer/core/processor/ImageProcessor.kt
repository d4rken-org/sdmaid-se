package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
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
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.SqueezerEligibility
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.ImageContentHasher
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
    private val settings: SqueezerSettings,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Result(
        val success: Set<CompressibleImage>,
        val failed: Map<CompressibleImage, Throwable>,
        val savedSpace: Long,
    )

    suspend fun process(
        targets: Set<CompressibleImage>,
        quality: Int,
    ): Result = withContext(dispatcherProvider.IO) {
        log(TAG) { "process(${targets.size} images, quality=$quality)" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        log(TAG, INFO) { "Processing ${targets.size} images" }

        val successful = mutableSetOf<CompressibleImage>()
        val failed = mutableMapOf<CompressibleImage, Throwable>()
        var totalSaved = 0L

        targets.forEachIndexed { index, image ->
            updateProgressCount(Progress.Count.Percent(index, targets.size))
            updateProgressSecondary(image.lookup.userReadablePath)

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
        }

        log(TAG, INFO) {
            "Processing complete: ${successful.size}/${targets.size} images, ${failed.size} failed, saved $totalSaved bytes"
        }

        Result(
            success = successful,
            failed = failed,
            savedSpace = totalSaved,
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

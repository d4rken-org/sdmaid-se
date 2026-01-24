package eu.darken.sdmse.compressor.core.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.compressor.core.CompressibleImage
import eu.darken.sdmse.compressor.core.Compressor
import eu.darken.sdmse.compressor.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.compressor.core.tasks.CompressorProcessTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException


class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewaySwitch: GatewaySwitch,
    private val exifPreserver: ExifPreserver,
    private val dispatcherProvider: DispatcherProvider,
    private val historyDatabase: CompressionHistoryDatabase,
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
        task: CompressorProcessTask,
        snapshot: Compressor.Data,
        quality: Int,
    ): Result = withContext(dispatcherProvider.IO) {
        log(TAG) { "process($task, quality=$quality)" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        val targets: Set<CompressibleImage> = when (val mode = task.mode) {
            is CompressorProcessTask.TargetMode.All -> snapshot.images
            is CompressorProcessTask.TargetMode.Selected -> {
                snapshot.images.filter { mode.targets.contains(it.identifier) }.toSet()
            }
        }

        log(TAG, INFO) { "Processing ${targets.size} images" }

        val successful = mutableSetOf<CompressibleImage>()
        val failed = mutableMapOf<CompressibleImage, Throwable>()
        var totalSaved = 0L

        targets.forEachIndexed { index, image ->
            updateProgressCount(Progress.Count.Percent(index, targets.size))
            updateProgressSecondary(image.lookup.userReadablePath)

            try {
                val saved = processImage(image, quality)
                successful.add(image)

                val contentHash = historyDatabase.computeContentHash(image.path)
                historyDatabase.recordCompression(contentHash)

                if (saved > 0) {
                    totalSaved += saved
                    log(TAG, VERBOSE) { "Compressed ${image.path}: saved $saved bytes" }
                } else {
                    log(TAG, INFO) { "No savings for ${image.path}, marking as processed" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to compress ${image.path}: ${e.message}" }
                failed[image] = e
            }
        }

        log(TAG, INFO) { "Processing complete: ${successful.size}/${targets.size} images, ${failed.size} failed, saved $totalSaved bytes" }

        Result(
            success = successful,
            failed = failed,
            savedSpace = totalSaved,
        )
    }

    private suspend fun processImage(image: CompressibleImage, quality: Int): Long {
        val originalSize = image.size
        val localPath = image.path as? LocalPath
            ?: throw IllegalArgumentException("Only local paths are supported: ${image.path}")

        val originalFile = File(localPath.path)
        val backupFile = File(originalFile.parent, ".sdmaid_backup_${originalFile.name}")
        val tempFile = File(originalFile.parent, ".sdmaid_compress_${originalFile.name}")

        try {
            val exifData = if (image.isJpeg) {
                exifPreserver.extractExif(originalFile)
            } else {
                null
            }

            val bitmap = decodeSampledBitmap(originalFile) ?: throw IllegalStateException("Failed to decode bitmap")

            val compressedSize = try {
                compressBitmapToFile(bitmap, image.mimeType, quality, tempFile)
            } finally {
                bitmap.recycle()
            }

            if (compressedSize >= originalSize) {
                log(TAG, VERBOSE) { "Compressed size >= original, skipping ${image.path}" }
                tempFile.delete()
                return 0
            }

            if (image.isJpeg) {
                exifPreserver.applyExif(tempFile.absolutePath, exifData)
            }

            if (!originalFile.renameTo(backupFile)) {
                throw IOException("Failed to create backup: ${backupFile.path}")
            }

            try {
                if (!tempFile.renameTo(originalFile)) {
                    tempFile.inputStream().use { input ->
                        originalFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (!tempFile.delete()) {
                        log(TAG, WARN) { "Failed to delete temp file: ${tempFile.path}" }
                    }
                }

                if (!originalFile.exists() || !originalFile.canRead()) {
                    throw IOException("Verification failed: compressed file not readable")
                }

                if (!backupFile.delete()) {
                    log(TAG, WARN) { "Failed to delete backup file: ${backupFile.path}" }
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Compression failed, restoring backup: ${e.message}" }
                originalFile.delete()
                if (!backupFile.renameTo(originalFile)) {
                    throw IOException("Failed to restore backup after error: ${e.message}", e)
                }
                throw e
            }

            notifyMediaScanner(originalFile)

            return originalSize - compressedSize
        } catch (e: Exception) {
            tempFile.delete()
            backupFile.delete()
            throw e
        }
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, MAX_DIMENSION)

        if (inSampleSize > 1) {
            log(TAG, VERBOSE) { "Using inSampleSize=$inSampleSize for ${options.outWidth}x${options.outHeight}" }
        }

        options.apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
        }

        return file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= maxDimension || (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @Suppress("DEPRECATION")
    private fun compressBitmapToFile(
        bitmap: Bitmap,
        mimeType: String,
        quality: Int,
        outputFile: File,
    ): Long {
        val format = when (mimeType) {
            CompressibleImage.MIME_TYPE_JPEG -> Bitmap.CompressFormat.JPEG
            CompressibleImage.MIME_TYPE_WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.JPEG
        }

        outputFile.outputStream().buffered().use { output ->
            bitmap.compress(format, quality, output)
        }
        return outputFile.length()
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
        private val TAG = logTag("Compressor", "Processor")
        private const val MAX_DIMENSION = 4096
    }
}

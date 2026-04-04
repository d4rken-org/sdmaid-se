package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
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
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val historyDatabase: CompressionHistoryDatabase,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Result(
        val success: Set<CompressibleVideo>,
        val failed: Map<CompressibleVideo, Throwable>,
        val savedSpace: Long,
    )

    suspend fun process(
        targets: Set<CompressibleVideo>,
        quality: Int,
    ): Result = withContext(dispatcherProvider.IO) {
        log(TAG) { "process(${targets.size} videos, quality=$quality)" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        val successful = mutableSetOf<CompressibleVideo>()
        val failed = mutableMapOf<CompressibleVideo, Throwable>()
        var totalSaved = 0L

        targets.forEachIndexed { index, video ->
            updateProgressCount(Progress.Count.Percent(index, targets.size))
            updateProgressSecondary(video.lookup.userReadablePath)

            try {
                val saved = processVideo(video, quality)
                successful.add(video)

                val contentHash = historyDatabase.computeVideoContentHash(video.path)
                historyDatabase.recordCompression(contentHash)

                if (saved > 0) {
                    totalSaved += saved
                    log(TAG, VERBOSE) { "Compressed ${video.path}: saved $saved bytes" }
                } else {
                    log(TAG, INFO) { "No savings for ${video.path}, marking as processed" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to compress ${video.path}: ${e.asLog()}" }
                failed[video] = e
            }
        }

        log(TAG, INFO) {
            "Processing complete: ${successful.size}/${targets.size} videos, ${failed.size} failed, saved $totalSaved bytes"
        }

        Result(
            success = successful,
            failed = failed,
            savedSpace = totalSaved,
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun processVideo(video: CompressibleVideo, quality: Int): Long {
        val originalSize = video.size
        val localPath = video.path as? LocalPath
            ?: throw IllegalArgumentException("Only local paths are supported: ${video.path}")

        val originalFile = File(localPath.path)
        val backupFile = File(originalFile.parent, ".sdmaid_backup_${originalFile.name}")
        val tempFile = File(originalFile.parent, ".sdmaid_compress_${originalFile.name}")

        var backupIsRestored = false

        try {
            val freeSpace = originalFile.parentFile?.usableSpace ?: 0L
            val requiredSpace = (originalSize * 1.1).toLong()
            if (freeSpace < requiredSpace) {
                throw IOException("Insufficient disk space: need $requiredSpace, have $freeSpace")
            }

            val targetBitrate = (video.bitrateBps * quality / 100)
                .coerceIn(MIN_BITRATE_BPS, Int.MAX_VALUE.toLong())

            log(TAG) { "Transcoding ${video.path}: ${video.bitrateBps}bps -> ${targetBitrate}bps" }

            transcodeVideo(originalFile, tempFile, targetBitrate)

            val compressedSize = tempFile.length()

            if (compressedSize >= originalSize) {
                log(TAG, VERBOSE) { "Compressed size >= original, skipping ${video.path}" }
                tempFile.delete()
                return 0
            }

            if (Bugs.isDryRun) {
                log(TAG, INFO) { "DRYRUN: Not compressing ${video.path}, would save ${originalSize - compressedSize} bytes" }
                tempFile.delete()
                return originalSize - compressedSize
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

                if (!originalFile.exists() || !originalFile.canRead() || originalFile.length() != compressedSize) {
                    throw IOException("Verification failed: compressed file not valid (expected $compressedSize bytes)")
                }

                if (!backupFile.delete()) {
                    log(TAG, WARN) { "Failed to delete backup file: ${backupFile.path}" }
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Compression failed, restoring backup: ${e.message}" }
                originalFile.delete()
                if (backupFile.renameTo(originalFile)) {
                    backupIsRestored = true
                } else {
                    throw IOException("Failed to restore backup after error: ${e.message}", e)
                }
                throw e
            }

            notifyMediaScanner(originalFile)

            return originalSize - compressedSize
        } catch (e: Exception) {
            tempFile.delete()
            if (!backupIsRestored) backupFile.delete()
            throw e
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun transcodeVideo(
        inputFile: File,
        outputFile: File,
        targetBitrateBps: Long,
    ) {
        val handlerThread = HandlerThread("sdmaid-transformer").apply { start() }

        try {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(handlerThread.looper)
                val encoderSettings = VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrateBps.toInt())
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setLooper(handlerThread.looper)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: androidx.media3.transformer.ExportResult,
                        ) {
                            cont.resume(Unit)
                        }

                        override fun onError(
                            composition: androidx.media3.transformer.Composition,
                            exportResult: androidx.media3.transformer.ExportResult,
                            exportException: androidx.media3.transformer.ExportException,
                        ) {
                            cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))

                handler.post {
                    transformer.start(mediaItem, outputFile.absolutePath)
                }

                cont.invokeOnCancellation {
                    handler.post {
                        transformer.cancel()
                        outputFile.delete()
                    }
                }
            }
        } finally {
            handlerThread.quitSafely()
        }
    }

    private fun notifyMediaScanner(file: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            null,
        )
    }

    companion object {
        private const val MIN_BITRATE_BPS = 100_000L
        private val TAG = logTag("Squeezer", "Video", "Processor")
    }
}

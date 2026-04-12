package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
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
import eu.darken.sdmse.squeezer.core.InsufficientStorageException
import eu.darken.sdmse.squeezer.core.SqueezerEligibility
import eu.darken.sdmse.squeezer.core.UnsupportedFormatException
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher
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
    private val videoContentHasher: VideoContentHasher,
    private val fileTransaction: FileTransaction,
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
                val outcome = processVideo(video, quality)
                successful.add(video)

                if (outcome.replaced) {
                    totalSaved += outcome.savedBytes
                    val identifier = videoContentHasher.computeHash(video.path)
                    historyDatabase.recordCompression(identifier.contentId)
                    log(TAG, VERBOSE) { "Compressed ${video.path}: saved ${outcome.savedBytes} bytes" }
                } else if (outcome.savedBytes == 0L) {
                    // Actual transcode ran and produced output >= original. Record so rescans
                    // don't burn cycles re-trying a file we already know won't shrink. Dry runs
                    // fall through to the else branch (they have savedBytes > 0 even though
                    // replaced = false). User's retry escape hatch is clearing history.
                    try {
                        val identifier = videoContentHasher.computeHash(video.path)
                        historyDatabase.recordNoSavings(identifier.contentId)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to record no-savings for ${video.path}: ${e.message}" }
                    }
                    log(TAG, INFO) { "Skipped ${video.path} (no savings, recorded)" }
                } else {
                    log(TAG, INFO) { "Skipped ${video.path} (dry-run)" }
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

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun processVideo(
        video: CompressibleVideo,
        quality: Int,
    ): FileTransaction.Outcome {
        val originalSize = video.size

        // TODO(gateway): Transformer.start() only accepts a filesystem-path String, so
        // transcoding is locked to raw java.io.File regardless of gateway escalation.
        // MediaScanner pre-filters by SqueezerEligibility at Mode.NORMAL, but state can
        // drift between scan and process (file moved / permissions changed / volume
        // remounted) — preflight here so we surface a typed failure instead of a
        // generic Transformer error downstream.
        val localPath = video.path as? LocalPath
            ?: throw IllegalArgumentException("Only local paths are supported: ${video.path}")

        val originalFile = File(localPath.path)

        val verdict = SqueezerEligibility.check(originalFile)
        if (verdict != SqueezerEligibility.Verdict.OK) {
            throw IOException("File no longer processable ($verdict): ${originalFile.path}")
        }

        // Reserve ~10% headroom on the source volume for the backup + temp copies that
        // FileTransaction will produce next to the original.
        val freeSpace = originalFile.parentFile?.usableSpace ?: 0L
        val requiredSpace = (originalSize * 1.1).toLong()
        if (freeSpace < requiredSpace) {
            throw InsufficientStorageException(requiredBytes = requiredSpace, availableBytes = freeSpace)
        }

        val targetBitrate = (video.bitrateBps * quality / 100)
            .coerceIn(MIN_BITRATE_BPS, Int.MAX_VALUE.toLong())

        log(TAG) { "Transcoding ${video.path}: ${video.bitrateBps}bps -> ${targetBitrate}bps" }

        val outcome = fileTransaction.replace(
            target = originalFile,
            dryRun = Bugs.isDryRun,
        ) { tempFile ->
            transcodeVideo(
                inputFile = originalFile,
                outputFile = tempFile,
                targetBitrateBps = targetBitrate,
                pathDisplay = video.lookup.userReadablePath,
            )
        }

        if (outcome.replaced) {
            notifyMediaScanner(originalFile)
        }

        return outcome
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun transcodeVideo(
        inputFile: File,
        outputFile: File,
        targetBitrateBps: Long,
        pathDisplay: CaString,
    ) {
        val handlerThread = HandlerThread("sdmaid-transformer").apply { start() }

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val handler = Handler(handlerThread.looper)
                // Transformer enforces thread affinity on the looper it was configured with, so
                // every Builder/addListener/start/getProgress/cancel call must run on the
                // handler thread. We capture the instance in a nullable var so the cancellation
                // handler can reach it, handling the race where cancel fires before the setup
                // post has even executed.
                var transformerRef: Transformer? = null

                handler.post {
                    try {
                        val progressHolder = ProgressHolder()

                        val encoderSettings = VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrateBps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            .build()

                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(encoderSettings)
                            .build()

                        // Audio policy: we pass the input through a Composition with
                        // setTransmuxAudio(true) to force deterministic audio passthrough
                        // regardless of Media3's muxer-support-conditional default. Without
                        // this, Media3 1.6 passthrough is contingent on the muxer accepting the
                        // input audio MIME type (works for typical MP4+AAC, but would silently
                        // re-encode for e.g. AC3 inside MP4). Explicit transmux makes behavior
                        // stable across Media3 versions.
                        val transformer = Transformer.Builder(context)
                            .setEncoderFactory(encoderFactory)
                            .setLooper(handlerThread.looper)
                            .build()
                        transformerRef = transformer

                        lateinit var poller: Runnable
                        poller = Runnable {
                            val state = transformer.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val pct = progressHolder.progress
                                updateProgressSecondary(
                                    caString { "${pathDisplay.get(this)} ($pct%)" }
                                )
                            }
                            handler.postDelayed(poller, PROGRESS_POLL_INTERVAL_MS)
                        }

                        transformer.addListener(object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                handler.removeCallbacks(poller)
                                cont.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                handler.removeCallbacks(poller)
                                cont.resumeWithException(mapExportException(exportException))
                            }
                        })

                        val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
                        val editedItem = EditedMediaItem.Builder(mediaItem).build()
                        val sequence = EditedMediaItemSequence(listOf(editedItem))
                        val composition = Composition.Builder(sequence)
                            .setTransmuxAudio(true)
                            .build()

                        transformer.start(composition, outputFile.absolutePath)
                        handler.postDelayed(poller, PROGRESS_POLL_INTERVAL_MS)
                    } catch (e: Throwable) {
                        log(TAG, ERROR) { "Transformer setup/start threw synchronously: ${e.asLog()}" }
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }

                cont.invokeOnCancellation {
                    handler.post {
                        val t = transformerRef
                        if (t != null) {
                            try {
                                t.cancel()
                            } catch (e: Throwable) {
                                log(TAG, WARN) { "transformer.cancel() failed: ${e.message}" }
                            }
                        }
                        if (outputFile.exists()) outputFile.delete()
                    }
                }
            }
        } finally {
            handlerThread.quitSafely()
        }
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun mapExportException(e: ExportException): Throwable {
        // Map broad Transformer error categories to typed exceptions. We avoid matching every
        // specific errorCode because Media3 adds and renames codes across versions. The
        // errorCodeName of format/codec failures reliably contains DECODER/ENCODER/FORMAT.
        val name = runCatching { e.errorCodeName }.getOrDefault("")
        val isFormatFailure = name.contains("DECODER", ignoreCase = true) ||
                name.contains("ENCODER", ignoreCase = true) ||
                name.contains("DECODING", ignoreCase = true) ||
                name.contains("ENCODING", ignoreCase = true) ||
                name.contains("UNSUPPORTED", ignoreCase = true)
        return if (isFormatFailure) {
            UnsupportedFormatException("Transformer format error ($name): ${e.message}", e)
        } else {
            e
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
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
        private val TAG = logTag("Squeezer", "Video", "Processor")
    }
}

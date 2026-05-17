package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4TimestampData
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.squeezer.core.UnsupportedFormatException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoTimestampPreserver: VideoTimestampPreserver,
) {

    /**
     * Progress callback invoked on the handler thread during transcoding.
     * [percent] is 0–100 when available, or -1 when progress is unavailable.
     */
    fun interface ProgressListener {
        fun onProgress(percent: Int)
    }

    @OptIn(UnstableApi::class)
    suspend fun transcode(
        inputFile: File,
        outputFile: File,
        targetBitrateBps: Long,
        progressListener: ProgressListener? = null,
    ) {
        // Capture the source mvhd timestamps so we can inject them into the muxer output
        // (#2388). Best-effort: a failure here just disables date preservation, gallery sort
        // then falls back to filesystem mtime which FileTransaction already preserves.
        val sourceTimestamps: VideoTimestampPreserver.TimestampData? = try {
            videoTimestampPreserver.extract(inputFile)
        } catch (e: Exception) {
            log(TAG, WARN) { "Timestamp extract failed for ${inputFile.path}: ${e.message}" }
            null
        }

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
                var pollerRef: Runnable? = null
                // Cancellation can race with setup — the setup runnable checks this flag
                // before starting the export so it can bail early instead of writing to a
                // file that will immediately be deleted.
                val cancellationRequested = AtomicBoolean(false)

                fun deleteOutput() {
                    if (outputFile.exists()) outputFile.delete()
                }

                handler.post {
                    if (cancellationRequested.get() || !cont.isActive) {
                        deleteOutput()
                        return@post
                    }
                    try {
                        val progressHolder = ProgressHolder()

                        val encoderSettings = VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrateBps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            .build()

                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(encoderSettings)
                            .build()

                        // MetadataProvider runs at muxer close() time on the muxer thread.
                        // Strip the default Mp4TimestampData (Media3 seeds it with "now") and
                        // replace it with the source's mvhd values so gallery DATE_TAKEN sort
                        // is preserved (#2388).
                        val muxerFactory = InAppMp4Muxer.Factory { entries ->
                            sourceTimestamps?.let { ts ->
                                entries.removeAll { it is Mp4TimestampData }
                                entries.add(
                                    Mp4TimestampData(
                                        ts.creationTimeMp4Seconds,
                                        ts.modificationTimeMp4Seconds,
                                    )
                                )
                            }
                        }

                        // Audio policy: we pass the input through a Composition with
                        // setTransmuxAudio(true) to force deterministic audio passthrough
                        // regardless of Media3's muxer-support-conditional default. Without
                        // this, Media3 1.6 passthrough is contingent on the muxer accepting the
                        // input audio MIME type (works for typical MP4+AAC, but would silently
                        // re-encode for e.g. AC3 inside MP4). Explicit transmux makes behavior
                        // stable across Media3 versions.
                        val transformer = Transformer.Builder(context)
                            .setEncoderFactory(encoderFactory)
                            .setMuxerFactory(muxerFactory)
                            .setLooper(handlerThread.looper)
                            .build()
                        transformerRef = transformer

                        lateinit var poller: Runnable
                        poller = Runnable {
                            val state = transformer.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                progressListener?.onProgress(progressHolder.progress)
                            }
                            handler.postDelayed(poller, PROGRESS_POLL_INTERVAL_MS)
                        }
                        pollerRef = poller

                        transformer.addListener(object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult,
                            ) {
                                handler.removeCallbacks(poller)
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                handler.removeCallbacks(poller)
                                if (cont.isActive) cont.resumeWithException(mapExportException(exportException))
                            }
                        })

                        if (cancellationRequested.get() || !cont.isActive) {
                            deleteOutput()
                            return@post
                        }

                        val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
                        val editedItem = EditedMediaItem.Builder(mediaItem).build()
                        val sequence = EditedMediaItemSequence.Builder(listOf(editedItem)).build()
                        val composition = Composition.Builder(sequence)
                            .setTransmuxAudio(true)
                            .build()

                        transformer.start(composition, outputFile.absolutePath)

                        if (cancellationRequested.get()) {
                            // Cancellation may have fired while start() was executing. The
                            // cancellation callback already queued handler-thread cleanup, so
                            // just avoid scheduling any more work here.
                            deleteOutput()
                            return@post
                        }

                        handler.postDelayed(poller, PROGRESS_POLL_INTERVAL_MS)
                    } catch (e: Throwable) {
                        log(TAG, ERROR) { "Transformer setup/start threw synchronously: ${e.asLog()}" }
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }

                cont.invokeOnCancellation {
                    cancellationRequested.set(true)
                    // Direct delete covers the normal case where setup already ran.
                    deleteOutput()
                    handler.post {
                        pollerRef?.let { handler.removeCallbacks(it) }
                        val t = transformerRef
                        if (t != null) {
                            try {
                                t.cancel()
                            } catch (e: Throwable) {
                                log(TAG, WARN) { "transformer.cancel() failed: ${e.message}" }
                            }
                        }
                        // Second delete covers early-cancel: if setup hadn't run when
                        // the direct delete above fired, it was a no-op. The setup
                        // block then ran and started writing outputFile before this
                        // cancellation block drained on the same handler thread.
                        deleteOutput()
                    }
                }
            }
        } finally {
            handlerThread.quitSafely()
        }
    }

    companion object {
        internal const val MIN_BITRATE_BPS = 100_000L
        private const val PROGRESS_POLL_INTERVAL_MS = 500L
        private val TAG = logTag("Squeezer", "Video", "Transcoder")

        @OptIn(UnstableApi::class)
        internal fun mapExportException(e: ExportException): Throwable {
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
    }
}

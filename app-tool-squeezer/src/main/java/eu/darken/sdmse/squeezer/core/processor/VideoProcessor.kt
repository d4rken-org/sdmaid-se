package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
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
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

class VideoProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoTranscoder: VideoTranscoder,
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
                    try {
                        val identifier = videoContentHasher.computeHash(video.path)
                        historyDatabase.recordCompression(identifier.contentId)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to record compression for ${video.path}: ${e.message}" }
                    }
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

        // NOTE: METADATA_KEY_BITRATE is total container bitrate (audio + video). For typical
        // phone video (AAC audio at 128-256 kbps vs several Mbps video), the difference is
        // negligible. For audio-heavy content this could cause unnecessary no-savings
        // transcodes. Future improvement: extract video-only bitrate via MediaExtractor.
        val targetBitrate = (video.bitrateBps * quality / 100)
            .coerceIn(VideoTranscoder.MIN_BITRATE_BPS, Int.MAX_VALUE.toLong())

        log(TAG) { "Transcoding ${video.path}: ${video.bitrateBps}bps -> ${targetBitrate}bps" }

        val outcome = fileTransaction.replace(
            target = originalFile,
            dryRun = Bugs.isDryRun,
        ) { tempFile ->
            videoTranscoder.transcode(
                inputFile = originalFile,
                outputFile = tempFile,
                targetBitrateBps = targetBitrate,
                progressListener = { pct ->
                    updateProgressSecondary(
                        caString { "${video.lookup.userReadablePath.get(this)} ($pct%)" }
                    )
                },
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
            null,
        )
    }

    companion object {
        private val TAG = logTag("Squeezer", "Video", "Processor")
    }
}

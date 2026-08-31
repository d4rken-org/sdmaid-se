package eu.darken.sdmse.squeezer.core.processor

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressSubCount
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.core.CompressionEstimator
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
        val success: Set<CompressibleVideo>,
        val failed: Map<CompressibleVideo, Throwable>,
        val savedSpace: Long,
    )

    /**
     * [itemOffset] / [itemTotal] carry the position of this batch inside a run that also processes
     * images, so the published counter is "file 2 of 10" across both passes instead of restarting.
     */
    suspend fun process(
        targets: Set<CompressibleVideo>,
        quality: Int,
        itemOffset: Int = 0,
        itemTotal: Int = targets.size,
    ): Result = withContext(dispatcherProvider.IO) {
        log(TAG) { "process(${targets.size} videos, quality=$quality, offset=$itemOffset, total=$itemTotal)" }

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

        val successful = mutableSetOf<CompressibleVideo>()
        val failed = mutableMapOf<CompressibleVideo, Throwable>()
        var totalSaved = 0L

        targets.forEachIndexed { index, video ->
            // One publish, not four: progress is exposed through throttleLatest(250), which
            // conflates. Separate mutations let a fast item show up with the new file name next to
            // the previous path and counter for up to a throttle window.
            updateProgress {
                (it ?: Progress.Data()).copy(
                    primary = eu.darken.sdmse.common.R.string.general_progress_processing_x
                        .toCaString(video.path.name),
                    secondary = video.lookup.userReadablePath,
                    count = Progress.Count.Counter(itemOffset + index, itemTotal),
                    subCount = Progress.Count.Indeterminate(),
                )
            }

            try {
                val outcome = processVideo(video, quality) { pct ->
                    updateProgressSubCount(Progress.Count.Percent(pct, 100))
                }
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
        onTranscodeProgress: (Int) -> Unit,
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
        val targetBitrate = CompressionEstimator
            .targetVideoBitrateBps(video.bitrateBps, quality)
            .coerceAtMost(Int.MAX_VALUE.toLong())

        log(TAG) { "Transcoding ${video.path}: ${video.bitrateBps}bps -> ${targetBitrate}bps" }

        val outcome = fileTransaction.replace(
            target = originalFile,
            dryRun = Bugs.isDryRun,
        ) { tempFile ->
            videoTranscoder.transcode(
                inputFile = originalFile,
                outputFile = tempFile,
                targetBitrateBps = targetBitrate,
                // The transcoder reports -1 while Media3 has no progress to give yet.
                progressListener = { pct -> if (pct >= 0) onTranscodeProgress(pct) },
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

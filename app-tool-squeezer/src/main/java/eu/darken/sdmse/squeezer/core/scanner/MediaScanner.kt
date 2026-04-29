package eu.darken.sdmse.squeezer.core.scanner

import android.media.MediaMetadataRetriever
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.types.match
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.squeezer.core.SqueezerEligibility
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryEntity
import eu.darken.sdmse.squeezer.core.history.ImageContentHasher
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher
import eu.darken.sdmse.squeezer.core.processor.ExifPreserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject


class MediaScanner @Inject constructor(
    private val exclusionManager: ExclusionManager,
    private val dispatcherProvider: DispatcherProvider,
    private val localGateway: LocalGateway,
    private val mimeTypeTool: MimeTypeTool,
    private val historyDatabase: CompressionHistoryDatabase,
    private val imageContentHasher: ImageContentHasher,
    private val videoContentHasher: VideoContentHasher,
    private val compressionEstimator: CompressionEstimator,
    private val exifPreserver: ExifPreserver,
    private val settings: SqueezerSettings,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    data class Options(
        val paths: Set<APath>,
        val minimumSize: Long,
        val minAge: Duration?,
        val enabledMimeTypes: Set<String>,
        val skipPreviouslyCompressed: Boolean,
        val compressionQuality: Int,
    )

    data class ScanResult(
        val items: Set<CompressibleMedia>,
        val skippedInaccessibleCount: Int,
    )

    private suspend fun createSearchFlow(paths: Set<APath>): Flow<APathLookup<*>> {
        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.SQUEEZER)
        log(TAG) { "Squeezer exclusions are: $exclusions" }
        log(TAG) { "Search paths: $paths" }

        // Walk in Mode.NORMAL only: Transformer / BitmapFactory can't read files that would
        // require root/ADB escalation anyway, so escalating here just wastes work. Paths are
        // normalized to LocalPath at save-time in SqueezerSetupViewModel — anything non-local
        // that survives is stale settings and gets dropped with a warning.
        return paths.asFlow()
            .flatMapMerge(2) { path ->
                val localPath = path as? LocalPath
                if (localPath == null) {
                    log(TAG, WARN) { "Skipping non-local root (stale settings?): $path" }
                    return@flatMapMerge emptyFlow()
                }
                val filter: suspend (APathLookup<*>) -> Boolean = filter@{ toCheck: APathLookup<*> ->
                    exclusions.none { it.match(toCheck) }
                }
                localGateway.walk(
                    path = localPath,
                    options = APathGateway.WalkOptions(onFilter = filter),
                    mode = LocalGateway.Mode.NORMAL,
                )
            }
    }

    suspend fun scan(options: Options): ScanResult {
        log(TAG) { "scan($options)" }

        if (options.paths.isEmpty()) {
            log(TAG) { "No search paths configured, returning empty result" }
            return ScanResult(items = emptySet(), skippedInaccessibleCount = 0)
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_searching)
        updateProgressSecondary()
        updateProgressCount(Progress.Count.Indeterminate())

        val skippedInaccessible = AtomicInteger(0)

        val searchPaths = options.paths
        val searchFlow = createSearchFlow(searchPaths)
            .flowOn(dispatcherProvider.IO)
            .buffer(1024)
            .filter { lookup ->
                if (!lookup.isFile) return@filter false

                // TODO(gateway): Transformer + BitmapFactory require raw java.io.File, so
                // files that would need root/ADB escalation are intentionally dropped. This
                // is a best-effort stat check — processors re-run it as a preflight to
                // catch state drift between scan and process.
                val verdict = SqueezerEligibility.check(lookup.lookedUp)
                if (verdict != SqueezerEligibility.Verdict.OK) {
                    if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping ($verdict): $lookup" }
                    skippedInaccessible.incrementAndGet()
                    return@filter false
                }

                val isGoodSize = lookup.size >= options.minimumSize
                if (!isGoodSize) {
                    if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (size): $lookup" }
                    return@filter false
                }

                options.minAge?.let { minAge ->
                    val cutoff = Instant.now().minus(minAge)
                    if (lookup.modifiedAt.isAfter(cutoff)) {
                        if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (too recent): $lookup" }
                        return@filter false
                    }
                }

                true
            }

        var scannedCount = 0
        val results = mutableSetOf<CompressibleMedia>()

        searchFlow.collect { lookup ->
            scannedCount++
            updateProgressCount(Progress.Count.Counter(scannedCount))
            updateProgressSecondary(lookup.userReadablePath)

            val mimeType = mimeTypeTool.determineMimeType(lookup)

            val isImage = mimeType in CompressibleImage.SUPPORTED_MIME_TYPES
            val isVideo = mimeType in CompressibleVideo.SUPPORTED_MIME_TYPES

            if (!isImage && !isVideo) {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (mime): $mimeType - $lookup" }
                return@collect
            }

            if (mimeType !in options.enabledMimeTypes) {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "Skipping (type disabled): $mimeType - $lookup" }
                return@collect
            }

            if (isImage) {
                val media = processImageCandidate(lookup, mimeType, options)
                if (media != null) results.add(media)
            } else if (isVideo) {
                val media = processVideoCandidate(lookup, mimeType, options)
                if (media != null) results.add(media)
            }
        }

        log(TAG) { "Scan complete: ${results.size} compressible media found, ${skippedInaccessible.get()} skipped (inaccessible)" }
        return ScanResult(items = results, skippedInaccessibleCount = skippedInaccessible.get())
    }

    private suspend fun processImageCandidate(
        lookup: APathLookup<*>,
        mimeType: String,
        options: Options,
    ): CompressibleImage? {
        val skipReason = if (options.skipPreviouslyCompressed) {
            val hasExifMarker = if (settings.writeExifMarker.value()) {
                try {
                    val localPath = lookup.lookedUp as? LocalPath
                    localPath?.let { exifPreserver.hasCompressionMarker(File(it.path)) } ?: false
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to check EXIF marker for $lookup: ${e.message}" }
                    false
                }
            } else {
                false
            }

            if (hasExifMarker) {
                "compressed (exif marker)"
            } else {
                try {
                    val identifier = imageContentHasher.computeHash(lookup.lookedUp)
                    when (historyDatabase.getOutcome(identifier.contentId)) {
                        CompressionHistoryEntity.Outcome.COMPRESSED -> "compressed (history)"
                        CompressionHistoryEntity.Outcome.TRIED_NO_SAVINGS -> "no savings (history)"
                        null -> null
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to compute content hash for $lookup: ${e.message}" }
                    null
                }
            }
        } else {
            null
        }

        if (skipReason != null) {
            log(TAG, VERBOSE) { "Skipping image ($skipReason): $lookup" }
            return null
        }

        val estimatedCompressedSize = compressionEstimator.estimateCompressedSize(
            lookup.size, mimeType, options.compressionQuality,
        )

        return CompressibleImage(
            lookup = lookup,
            mimeType = mimeType,
            estimatedCompressedSize = estimatedCompressedSize,
            wasCompressedBefore = false,
        ).also {
            log(TAG, VERBOSE) { "Found compressible image: $it" }
        }
    }

    private suspend fun processVideoCandidate(
        lookup: APathLookup<*>,
        mimeType: String,
        options: Options,
    ): CompressibleVideo? {
        val metadata = try {
            withContext(dispatcherProvider.IO) { extractVideoMetadata(lookup) }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to extract video metadata for $lookup: ${e.message}" }
            return null
        }

        if (metadata == null) {
            log(TAG, VERBOSE) { "Skipping (no video metadata): $lookup" }
            return null
        }

        if (metadata.videoWidth <= 0) {
            log(TAG, VERBOSE) { "Skipping audio-only file (no video track): $lookup" }
            return null
        }

        val skipReason = if (options.skipPreviouslyCompressed) {
            try {
                val identifier = videoContentHasher.computeHash(lookup.lookedUp)
                when (historyDatabase.getOutcome(identifier.contentId)) {
                    CompressionHistoryEntity.Outcome.COMPRESSED -> "compressed (history)"
                    CompressionHistoryEntity.Outcome.TRIED_NO_SAVINGS -> "no savings (history)"
                    null -> null
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to compute video content hash for $lookup: ${e.message}" }
                null
            }
        } else {
            null
        }

        if (skipReason != null) {
            log(TAG, VERBOSE) { "Skipping video ($skipReason): $lookup" }
            return null
        }

        val estimatedCompressedSize = compressionEstimator.estimateVideoSize(
            originalSize = lookup.size,
            durationMs = metadata.durationMs,
            originalBitrateBps = metadata.bitrateBps,
            quality = options.compressionQuality,
        )

        return CompressibleVideo(
            lookup = lookup,
            mimeType = mimeType,
            estimatedCompressedSize = estimatedCompressedSize,
            wasCompressedBefore = false,
            durationMs = metadata.durationMs,
            bitrateBps = metadata.bitrateBps,
        ).also {
            log(TAG, VERBOSE) { "Found compressible video: $it" }
        }
    }

    private fun extractVideoMetadata(lookup: APathLookup<*>): VideoMetadata? {
        val localPath = lookup.lookedUp as? LocalPath ?: return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(File(localPath.path).absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: return null
            if (durationMs <= 0) return null

            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0

            // NOTE: METADATA_KEY_BITRATE is total container bitrate (audio + video combined).
            // For typical phone video this is fine (audio << video), but for audio-heavy
            // content it inflates the estimate. Future improvement: extract video-only
            // bitrate via MediaExtractor track-level metadata.
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            var bitrateBps = bitrateStr?.toLongOrNull() ?: -1L

            if (bitrateBps <= 0) {
                bitrateBps = (lookup.size * 8 * 1000 / durationMs).coerceAtMost(MAX_BITRATE_BPS)
            }

            VideoMetadata(
                durationMs = durationMs,
                bitrateBps = bitrateBps.coerceAtMost(MAX_BITRATE_BPS),
                videoWidth = videoWidth,
            )
        } catch (e: Exception) {
            log(TAG, WARN) { "MediaMetadataRetriever failed for $lookup: ${e.message}" }
            null
        } finally {
            retriever.release()
        }
    }

    private data class VideoMetadata(
        val durationMs: Long,
        val bitrateBps: Long,
        val videoWidth: Int,
    )

    companion object {
        private const val MAX_BITRATE_BPS = 200_000_000L // 200 Mbps
        private val TAG = logTag("Squeezer", "Scanner")
    }
}

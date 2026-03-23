package eu.darken.sdmse.deduplicator.core.scanner.media

import android.content.Context
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.deduplicator.R
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.Sleuth
import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.AudioFingerprinter
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedList
import java.util.UUID
import javax.inject.Inject

class MediaSleuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val mimeTypeTool: MimeTypeTool,
    private val audioFingerprinter: AudioFingerprinter,
    private val comparator: MediaComparator,
) : Sleuth {

    private val progressPub = MutableStateFlow<Progress.Data?>(Progress.Data())
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    override suspend fun investigate(searchFlow: Flow<APathLookup<*>>): Set<MediaDuplicate.Group> {
        log(TAG) { "investigate($searchFlow):..." }
        updateProgressPrimary(R.string.deduplicator_detection_method_media_title.toCaString())
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_searching)

        // Collect suspects with cached MIME types to avoid redundant detection
        val suspectsWithMime = searchFlow
            .toSet()
            .filter { it.lookedUp is LocalPath }
            .mapNotNull { item ->
                val mime = mimeTypeTool.determineMimeType(item)
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    item to mime
                } else {
                    null
                }
            }

        updateProgressSecondary(R.string.deduplicator_progress_comparing_files)
        updateProgressCount(Progress.Count.Percent(suspectsWithMime.size))

        val hashStart = System.currentTimeMillis()
        val codecSemaphore = Semaphore(MAX_CONCURRENT_DECODERS)

        val hashedItems: List<HashedMedia> = suspectsWithMime
            .asFlow()
            .flowOn(dispatcherProvider.IO)
            .flatMapMerge { (item, mime) ->
                flow {
                    updateProgressSecondary(item.userReadablePath)
                    val start = System.currentTimeMillis()
                    val result = try {
                        codecSemaphore.withPermit {
                            withTimeoutOrNull(PER_FILE_TIMEOUT_MS) {
                                hashMediaFile(item, mime)
                            }
                        }
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to hash $item: $e" }
                        null
                    }

                    if (Bugs.isTrace) {
                        val stop = System.currentTimeMillis()
                        log(TAG, VERBOSE) {
                            "Media took ${String.format("%4d", stop - start)}ms - ${item.path}"
                        }
                    }

                    increaseProgress()
                    emit(result)
                }
            }
            .filterNotNull()
            .toList()

        val hashMs = System.currentTimeMillis() - hashStart
        val avgMs = if (hashedItems.isNotEmpty()) hashMs / hashedItems.size else 0
        val audioCount = hashedItems.count { it.audioResult != null }
        val videoCount = hashedItems.count { it.isVideo }
        log(TAG) {
            "Hashed ${hashedItems.size} media files in ${hashMs}ms " +
                    "(avg ${avgMs}ms/file, audio=$audioCount, video=$videoCount, " +
                    "concurrency=$MAX_CONCURRENT_DECODERS)"
        }

        // Group by duration bucket before pairwise comparison
        val durationBuckets = hashedItems.groupBy { it.durationBucket }
        val comparableBuckets = durationBuckets.values.filter { it.size >= 2 }
        val filesToCompare = comparableBuckets.sumOf { it.size }
        log(TAG) {
            "${durationBuckets.size} duration buckets, ${comparableBuckets.size} with 2+ items ($filesToCompare files to compare)"
        }

        updateProgressSecondary(R.string.deduplicator_progress_comparing_files.toCaString())
        updateProgressCount(Progress.Count.Percent(0, filesToCompare))

        val hashBuckets = mutableSetOf<Set<Pair<APathLookup<*>, Double>>>()
        var comparedCount = 0

        comparableBuckets.forEach { bucketItems ->
            findSimilarGroups(bucketItems, hashBuckets)
            comparedCount += bucketItems.size
            updateProgressCount(Progress.Count.Percent(comparedCount, filesToCompare))
        }

        val hashStop = System.currentTimeMillis()
        log(TAG) { "Media investigation took ${(hashStop - hashStart)}ms, found ${hashBuckets.size} groups" }

        return hashBuckets.map { items: Set<Pair<APathLookup<*>, Double>> ->
            val itemMap = hashedItems.associateBy { it.lookup }
            MediaDuplicate.Group(
                identifier = Duplicate.Group.Id(UUID.randomUUID().toString()),
                duplicates = items.map { (item, similarity) ->
                    val hashed = itemMap[item]!!
                    MediaDuplicate(
                        lookup = item,
                        audioHash = hashed.audioResult,
                        frameHashes = hashed.cachedFrameHashes,
                        similarity = similarity,
                    )
                }.toSet()
            )
        }.toSet()
    }

    private fun findSimilarGroups(
        items: List<HashedMedia>,
        hashBuckets: MutableSet<Set<Pair<APathLookup<*>, Double>>>,
    ) {
        val remainingItems = LinkedList(items)

        while (remainingItems.isNotEmpty()) {
            val target = remainingItems.removeFirst()

            val others = remainingItems
                .mapNotNull { other ->
                    val sim = computeSimilarity(target, other) ?: return@mapNotNull null
                    if (Bugs.isTrace || sim > MediaComparator.REJECT_THRESHOLD) {
                        log(TAG, VERBOSE) {
                            "${String.format("%.2f%%", sim * 100)} : ${target.lookup.path} <-> ${other.lookup.path}"
                        }
                    }
                    if (sim > MediaComparator.ACCEPT_THRESHOLD) {
                        other to sim
                    } else if (sim > MediaComparator.REJECT_THRESHOLD) {
                        // In tiebreaker range — lazy frame pHash
                        val tiebreakerSim = computeWithTiebreaker(target, other, sim)
                        if (tiebreakerSim != null && tiebreakerSim > MediaComparator.ACCEPT_THRESHOLD) {
                            log(TAG, VERBOSE) {
                                "Tiebreaker: ${String.format("%.2f%%", tiebreakerSim * 100)} " +
                                        "(audio=${String.format("%.2f%%", sim * 100)})"
                            }
                            other to tiebreakerSim
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

            remainingItems.removeAll(others.map { it.first }.toSet())

            if (others.isEmpty()) continue

            val targetWithOthers = others
                .map { (other, sim) -> other.lookup to sim }
                .plus(target.lookup to others.map { it.second }.average())
                .toSet()
            hashBuckets.add(targetWithOthers)
        }
    }

    private fun HashedMedia.toMediaInfo(reason: String) = MediaComparator.MediaInfo(
        audioResult = audioResult,
        frameHashes = getOrComputeFrameHashes(reason),
        isVideo = isVideo,
    )

    private fun computeSimilarity(a: HashedMedia, b: HashedMedia): Double? {
        // Lazy: only compute frame hashes for video-no-audio pairs
        val reason = "no audio track"
        val aInfo = MediaComparator.MediaInfo(
            audioResult = a.audioResult,
            frameHashes = if (a.audioResult == null && a.isVideo) a.getOrComputeFrameHashes(reason) else emptyList(),
            isVideo = a.isVideo,
        )
        val bInfo = MediaComparator.MediaInfo(
            audioResult = b.audioResult,
            frameHashes = if (b.audioResult == null && b.isVideo) b.getOrComputeFrameHashes(reason) else emptyList(),
            isVideo = b.isVideo,
        )
        return comparator.computeSimilarity(aInfo, bInfo)
    }

    private fun computeWithTiebreaker(a: HashedMedia, b: HashedMedia, audioSim: Double): Double? {
        val reason = "audio tiebreaker"
        return comparator.computeWithTiebreaker(a.toMediaInfo(reason), b.toMediaInfo(reason), audioSim)
    }

    private fun hashMediaFile(item: APathLookup<*>, mime: String): HashedMedia? {
        val filePath = item.lookedUp.asFile().absolutePath
        val isVideo = mime.startsWith("video/")

        val audioResult = audioFingerprinter.fingerprint(filePath)

        // For video without audio, we still need duration for bucketing
        val durationMs = audioResult?.durationMs ?: extractDuration(filePath)

        // Must be a video or have audio
        if (audioResult == null && !isVideo) return null

        // For video without audio, compute frame hashes now (during hashing phase)
        // to benefit from parallel processing. Video+audio files keep lazy frame hashes
        // since they rarely need them (only for tiebreaker).
        val earlyFrameHashes = if (audioResult == null && isVideo) {
            try {
                extractFrameHashes(filePath, durationMs, "no audio track")
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to extract frame hashes: $e" }
                emptyList()
            }
        } else {
            null
        }

        // Video without audio must have frame hashes to be useful
        if (audioResult == null && earlyFrameHashes.isNullOrEmpty()) return null

        return HashedMedia(
            lookup = item,
            audioResult = audioResult,
            filePath = filePath,
            isVideo = isVideo,
            durationMs = durationMs,
            precomputedFrameHashes = earlyFrameHashes,
        )
    }

    private fun extractFrameHashes(filePath: String, durationMs: Long, reason: String = ""): List<PHasher.Result> {
        if (durationMs <= 0) return emptyList()

        val start = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        val pHasher = PHasher()
        return try {
            retriever.setDataSource(filePath)
            // Extract 5 frames at 10%, 30%, 50%, 70%, 90% of duration
            val timestamps = FRAME_POSITIONS.map { pct -> (durationMs * pct).toLong() * 1000 } // ms → µs

            val hashes = timestamps.mapNotNull { timeUs ->
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@mapNotNull null
                pHasher.calc(bitmap)
            }

            val elapsed = System.currentTimeMillis() - start
            val fileName = filePath.substringAfterLast('/')
            val reasonSuffix = if (reason.isNotEmpty()) " ($reason)" else ""
            log(TAG) { "Frames [$fileName] ${hashes.size}/${timestamps.size} frames in ${elapsed}ms$reasonSuffix" }
            hashes
        } finally {
            retriever.release()
        }
    }

    private fun extractDuration(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    inner class HashedMedia(
        val lookup: APathLookup<*>,
        val audioResult: AudioFingerprinter.Result?,
        val filePath: String,
        val isVideo: Boolean,
        val durationMs: Long,
        precomputedFrameHashes: List<PHasher.Result>? = null,
    ) {
        val durationBucket: Long
            get() = durationMs / DURATION_BUCKET_MS

        // Frame hashes — pre-computed for video-no-audio, lazy for video+audio (tiebreaker only)
        var cachedFrameHashes: List<PHasher.Result> = precomputedFrameHashes ?: emptyList()
            private set
        private var frameHashesComputed = precomputedFrameHashes != null

        fun getOrComputeFrameHashes(reason: String = ""): List<PHasher.Result> {
            if (!frameHashesComputed) {
                frameHashesComputed = true
                if (isVideo) {
                    updateProgressSecondary(lookup.userReadablePath)
                    cachedFrameHashes = try {
                        extractFrameHashes(filePath, durationMs, reason)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to extract frame hashes: $e" }
                        emptyList()
                    }
                }
            }
            return cachedFrameHashes
        }
    }

    companion object {
        private const val MAX_CONCURRENT_DECODERS = 4
        private const val PER_FILE_TIMEOUT_MS = 30_000L
        private const val DURATION_BUCKET_MS = 5_000L
        private val FRAME_POSITIONS = doubleArrayOf(0.10, 0.30, 0.50, 0.70, 0.90)
        private val TAG = logTag("Deduplicator", "Sleuth", "Media")
    }
}

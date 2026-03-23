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
        log(TAG) {
            "${durationBuckets.size} duration buckets, ${comparableBuckets.size} with 2+ items " +
                "(${comparableBuckets.sumOf { it.size }} files to compare)"
        }

        val hashBuckets = mutableSetOf<Set<Pair<APathLookup<*>, Double>>>()

        comparableBuckets.forEach { bucketItems ->
            findSimilarGroups(bucketItems, hashBuckets)
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
                        frameHash = hashed.cachedFrameHash,
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

    private fun HashedMedia.toMediaInfo() = MediaComparator.MediaInfo(
        audioResult = audioResult,
        frameHash = getOrComputeFrameHash(),
        isVideo = isVideo,
    )

    private fun computeSimilarity(a: HashedMedia, b: HashedMedia): Double? {
        // Lazy: only compute frame hash for video-no-audio pairs
        val aInfo = MediaComparator.MediaInfo(
            audioResult = a.audioResult,
            frameHash = if (a.audioResult == null && a.isVideo) a.getOrComputeFrameHash() else null,
            isVideo = a.isVideo,
        )
        val bInfo = MediaComparator.MediaInfo(
            audioResult = b.audioResult,
            frameHash = if (b.audioResult == null && b.isVideo) b.getOrComputeFrameHash() else null,
            isVideo = b.isVideo,
        )
        return comparator.computeSimilarity(aInfo, bInfo)
    }

    private fun computeWithTiebreaker(a: HashedMedia, b: HashedMedia, audioSim: Double): Double? {
        return comparator.computeWithTiebreaker(a.toMediaInfo(), b.toMediaInfo(), audioSim)
    }

    private fun hashMediaFile(item: APathLookup<*>, mime: String): HashedMedia? {
        val filePath = item.lookedUp.asFile().absolutePath
        val isVideo = mime.startsWith("video/")

        // Audio fingerprint only — frame hash computed lazily on demand
        val audioResult = audioFingerprinter.fingerprint(filePath)

        // For video without audio, we still need duration for bucketing
        val durationMs = audioResult?.durationMs ?: extractDuration(filePath)

        // Must be a video (for lazy frame hash) or have audio
        if (audioResult == null && !isVideo) return null

        return HashedMedia(
            lookup = item,
            audioResult = audioResult,
            filePath = filePath,
            isVideo = isVideo,
            durationMs = durationMs,
        )
    }

    private fun extractFrameHash(filePath: String): PHasher.Result? {
        val retrieverStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val retrieverMs = System.currentTimeMillis() - retrieverStart

            val phashStart = System.currentTimeMillis()
            val result = PHasher().calc(bitmap)
            val phashMs = System.currentTimeMillis() - phashStart

            val fileName = filePath.substringAfterLast('/')
            log(TAG) { "Frame [$fileName] retriever=${retrieverMs}ms phash=${phashMs}ms" }
            result
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
    ) {
        val durationBucket: Long
            get() = durationMs / DURATION_BUCKET_MS

        // Lazy frame hash — only computed when tiebreaker needs it
        var cachedFrameHash: PHasher.Result? = null
            private set
        private var frameHashComputed = false

        fun getOrComputeFrameHash(): PHasher.Result? {
            if (!frameHashComputed) {
                frameHashComputed = true
                if (isVideo) {
                    cachedFrameHash = try {
                        extractFrameHash(filePath)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to extract frame hash: $e" }
                        null
                    }
                }
            }
            return cachedFrameHash
        }
    }

    companion object {
        private const val MAX_CONCURRENT_DECODERS = 4
        private const val PER_FILE_TIMEOUT_MS = 30_000L
        private const val DURATION_BUCKET_MS = 5_000L
        private val TAG = logTag("Deduplicator", "Sleuth", "Media")
    }
}

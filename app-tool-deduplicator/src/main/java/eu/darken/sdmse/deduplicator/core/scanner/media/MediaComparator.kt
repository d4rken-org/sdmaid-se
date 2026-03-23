package eu.darken.sdmse.deduplicator.core.scanner.media

import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.AudioFingerprinter
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import javax.inject.Inject

class MediaComparator @Inject constructor() {

    data class MediaInfo(
        val audioResult: AudioFingerprinter.Result?,
        val frameHashes: List<PHasher.Result>,
        val isVideo: Boolean,
    )

    fun computeSimilarity(a: MediaInfo, b: MediaInfo): Double? {
        // Don't cross-match audio-only with video-no-audio
        val aIsAudioOnly = a.audioResult != null && !a.isVideo
        val bIsAudioOnly = b.audioResult != null && !b.isVideo
        val aIsVideoNoAudio = a.audioResult == null && a.isVideo
        val bIsVideoNoAudio = b.audioResult == null && b.isVideo

        if (aIsAudioOnly && bIsVideoNoAudio) return null
        if (aIsVideoNoAudio && bIsAudioOnly) return null

        // Both have audio → use audio similarity
        if (a.audioResult != null && b.audioResult != null) {
            return a.audioResult.similarityTo(b.audioResult)
        }

        // Both video-no-audio → use multi-frame hash comparison
        if (a.isVideo && b.isVideo && a.audioResult == null && b.audioResult == null) {
            val frameSim = averageFrameSimilarity(a.frameHashes, b.frameHashes) ?: return null
            return if (frameSim > VISUAL_ONLY_THRESHOLD) frameSim else null
        }

        return null
    }

    fun computeWithTiebreaker(a: MediaInfo, b: MediaInfo, audioSim: Double): Double? {
        if (!a.isVideo || !b.isVideo) return null
        val frameSim = averageFrameSimilarity(a.frameHashes, b.frameHashes) ?: return null
        // Weighted: 70% audio, 30% visual
        return audioSim * 0.7 + frameSim * 0.3
    }

    /**
     * Average pHash similarity across paired frames.
     * Compares frames at the same index (both extracted at matching timestamps).
     * Falls back to comparing however many pairs are available.
     */
    fun averageFrameSimilarity(a: List<PHasher.Result>, b: List<PHasher.Result>): Double? {
        if (a.isEmpty() || b.isEmpty()) return null
        val pairs = minOf(a.size, b.size)
        val totalSim = (0 until pairs).sumOf { i -> a[i].similarityTo(b[i]) }
        return totalSim / pairs
    }

    companion object {
        const val ACCEPT_THRESHOLD = 0.95
        const val REJECT_THRESHOLD = 0.85
        const val VISUAL_ONLY_THRESHOLD = 0.92
    }
}

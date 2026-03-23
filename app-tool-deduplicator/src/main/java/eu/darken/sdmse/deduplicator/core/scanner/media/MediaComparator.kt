package eu.darken.sdmse.deduplicator.core.scanner.media

import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.AudioFingerprinter
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import javax.inject.Inject

class MediaComparator @Inject constructor() {

    data class MediaInfo(
        val audioResult: AudioFingerprinter.Result?,
        val frameHash: PHasher.Result?,
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

        // Both video-no-audio → use frame hash comparison
        if (a.isVideo && b.isVideo && a.audioResult == null && b.audioResult == null) {
            val aHash = a.frameHash ?: return null
            val bHash = b.frameHash ?: return null
            val frameSim = aHash.similarityTo(bHash)
            return if (frameSim > VISUAL_ONLY_THRESHOLD) frameSim else null
        }

        return null
    }

    fun computeWithTiebreaker(a: MediaInfo, b: MediaInfo, audioSim: Double): Double? {
        if (!a.isVideo || !b.isVideo) return null
        val aHash = a.frameHash ?: return null
        val bHash = b.frameHash ?: return null
        val frameSim = aHash.similarityTo(bHash)
        // Weighted: 70% audio, 30% visual
        return audioSim * 0.7 + frameSim * 0.3
    }

    companion object {
        const val ACCEPT_THRESHOLD = 0.95
        const val REJECT_THRESHOLD = 0.85
        const val VISUAL_ONLY_THRESHOLD = 0.92
    }
}

package eu.darken.sdmse.deduplicator.core.scanner.media

import eu.darken.sdmse.deduplicator.core.scanner.media.MediaComparator.MediaInfo
import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.AudioFingerprinter
import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.FingerprintCalculator
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MediaComparisonTest : BaseTest() {

    private val comparator = MediaComparator()

    private fun audioResult(vararg fingerprint: Long, durationMs: Long = 10000L) = AudioFingerprinter.Result(
        fingerprint = FingerprintCalculator.Result(fingerprint = longArrayOf(*fingerprint)),
        durationMs = durationMs,
    )

    private fun frameHash(hash: Long) = PHasher.Result(hash = hash)

    // Identical audio fingerprint
    private val audioA = audioResult(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)
    private val audioB = audioResult(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)

    // Completely different audio
    private val audioDifferent = audioResult(-1L, -1L, -1L, -1L)

    private val frameA = frameHash(0x123456789ABCDEF0L)
    private val frameB = frameHash(0x123456789ABCDEF0L) // Same
    private val frameDifferent = frameHash(0x0EDCBA9876543210L)

    // --- audio+audio comparisons ---

    @Test
    fun `audio + audio - uses audio similarity`() {
        val a = MediaInfo(audioResult = audioA, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHash = frameDifferent, isVideo = true)

        val sim = comparator.computeSimilarity(a, b)
        sim.shouldNotBeNull()
        // Audio fingerprints are identical, so similarity should be 1.0
        // Frame hashes are different, but audio takes precedence
        sim shouldBe 1.0
    }

    @Test
    fun `audio-only + audio-only - uses audio similarity`() {
        val a = MediaInfo(audioResult = audioA, frameHash = null, isVideo = false)
        val b = MediaInfo(audioResult = audioB, frameHash = null, isVideo = false)

        val sim = comparator.computeSimilarity(a, b)
        sim.shouldNotBeNull()
        sim shouldBe 1.0
    }

    // --- cross-type rejection ---

    @Test
    fun `audio-only + video-no-audio - returns null`() {
        val audioOnly = MediaInfo(audioResult = audioA, frameHash = null, isVideo = false)
        val videoNoAudio = MediaInfo(audioResult = null, frameHash = frameA, isVideo = true)

        comparator.computeSimilarity(audioOnly, videoNoAudio).shouldBeNull()
    }

    @Test
    fun `video-no-audio + audio-only - returns null`() {
        val videoNoAudio = MediaInfo(audioResult = null, frameHash = frameA, isVideo = true)
        val audioOnly = MediaInfo(audioResult = audioA, frameHash = null, isVideo = false)

        comparator.computeSimilarity(videoNoAudio, audioOnly).shouldBeNull()
    }

    // --- video-no-audio comparisons ---

    @Test
    fun `video-no-audio + video-no-audio - uses frame hash`() {
        val a = MediaInfo(audioResult = null, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = null, frameHash = frameB, isVideo = true)

        val sim = comparator.computeSimilarity(a, b)
        sim.shouldNotBeNull()
        // Identical frame hashes
        sim shouldBe 1.0
    }

    @Test
    fun `video-no-audio + video-no-audio - below threshold returns null`() {
        val a = MediaInfo(audioResult = null, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = null, frameHash = frameDifferent, isVideo = true)

        val sim = comparator.computeSimilarity(a, b)
        // Frame similarity of very different hashes should be below 0.92 threshold
        // Either null (below threshold) or a low value
        if (sim != null) {
            sim shouldBeLessThan MediaComparator.VISUAL_ONLY_THRESHOLD
        }
    }

    @Test
    fun `video-no-audio + missing frame hash - returns null`() {
        val a = MediaInfo(audioResult = null, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = null, frameHash = null, isVideo = true)

        comparator.computeSimilarity(a, b).shouldBeNull()
    }

    // --- no match possible ---

    @Test
    fun `no audio no frame hash - returns null`() {
        val a = MediaInfo(audioResult = null, frameHash = null, isVideo = false)
        val b = MediaInfo(audioResult = null, frameHash = null, isVideo = false)

        comparator.computeSimilarity(a, b).shouldBeNull()
    }

    // --- tiebreaker ---

    @Test
    fun `tiebreaker - both video with frame hash - weighted 70 30`() {
        val a = MediaInfo(audioResult = audioA, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHash = frameB, isVideo = true)

        val audioSim = 0.90
        val result = comparator.computeWithTiebreaker(a, b, audioSim)

        result.shouldNotBeNull()
        // Frame hashes are identical so frameSim = 1.0
        // Expected: 0.90 * 0.7 + 1.0 * 0.3 = 0.63 + 0.30 = 0.93
        result shouldBe 0.90 * 0.7 + 1.0 * 0.3
    }

    @Test
    fun `tiebreaker - non-video returns null`() {
        val a = MediaInfo(audioResult = audioA, frameHash = null, isVideo = false)
        val b = MediaInfo(audioResult = audioB, frameHash = null, isVideo = false)

        comparator.computeWithTiebreaker(a, b, 0.90).shouldBeNull()
    }

    @Test
    fun `tiebreaker - missing frame hash returns null`() {
        val a = MediaInfo(audioResult = audioA, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHash = null, isVideo = true)

        comparator.computeWithTiebreaker(a, b, 0.90).shouldBeNull()
    }

    @Test
    fun `tiebreaker - one non-video returns null`() {
        val a = MediaInfo(audioResult = audioA, frameHash = frameA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHash = null, isVideo = false)

        comparator.computeWithTiebreaker(a, b, 0.90).shouldBeNull()
    }
}

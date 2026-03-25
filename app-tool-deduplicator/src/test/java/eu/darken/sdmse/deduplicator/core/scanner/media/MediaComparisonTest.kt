package eu.darken.sdmse.deduplicator.core.scanner.media

import eu.darken.sdmse.deduplicator.core.scanner.media.MediaComparator.MediaInfo
import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.AudioFingerprinter
import eu.darken.sdmse.deduplicator.core.scanner.media.audiohash.FingerprintCalculator
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHashBits
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MediaComparisonTest : BaseTest() {

    private val comparator = MediaComparator()

    private fun audioResult(vararg fingerprint: Long, durationMs: Long = 10000L) = AudioFingerprinter.Result(
        fingerprints = listOf(FingerprintCalculator.Result(fingerprint = longArrayOf(*fingerprint))),
        durationMs = durationMs,
    )

    private fun multiAudioResult(
        segments: List<LongArray>,
        durationMs: Long = 10000L,
    ) = AudioFingerprinter.Result(
        fingerprints = segments.map { FingerprintCalculator.Result(fingerprint = it) },
        durationMs = durationMs,
    )

    private fun frameHash(hash: Long) = PHasher.Result(hash = PHashBits(hash))
    private fun frameHashes(vararg hashes: Long) = hashes.map { PHasher.Result(hash = PHashBits(it)) }

    // Identical audio fingerprint
    private val audioA = audioResult(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)
    private val audioB = audioResult(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)

    // Completely different audio
    private val audioDifferent = audioResult(-1L, -1L, -1L, -1L)

    // 5 identical frame hashes (simulating 5 frames from same video)
    private val framesA = frameHashes(0x1111L, 0x2222L, 0x3333L, 0x4444L, 0x5555L)
    private val framesB = frameHashes(0x1111L, 0x2222L, 0x3333L, 0x4444L, 0x5555L) // Same
    private val framesDifferent = frameHashes(0x0AAAAL, 0x0BBBBL, 0x0CCCCL, 0x0DDDDL, 0x0EEEEL)

    // --- audio+audio comparisons ---

    @Test
    fun `audio + audio - uses audio similarity`() {
        val a = MediaInfo(audioResult = audioA, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHashes = framesDifferent, isVideo = true)

        val sim = comparator.computeSimilarity(a, b)
        sim.shouldNotBeNull()
        sim shouldBe 1.0
    }

    @Test
    fun `audio-only + audio-only - uses audio similarity`() {
        val a = MediaInfo(audioResult = audioA, frameHashes = emptyList(), isVideo = false)
        val b = MediaInfo(audioResult = audioB, frameHashes = emptyList(), isVideo = false)

        val sim = comparator.computeSimilarity(a, b)
        sim.shouldNotBeNull()
        sim shouldBe 1.0
    }

    // --- cross-type rejection ---

    @Test
    fun `audio-only + video-no-audio - returns null`() {
        val audioOnly = MediaInfo(audioResult = audioA, frameHashes = emptyList(), isVideo = false)
        val videoNoAudio = MediaInfo(audioResult = null, frameHashes = framesA, isVideo = true)

        comparator.computeSimilarity(audioOnly, videoNoAudio).shouldBeNull()
    }

    @Test
    fun `video-no-audio + audio-only - returns null`() {
        val videoNoAudio = MediaInfo(audioResult = null, frameHashes = framesA, isVideo = true)
        val audioOnly = MediaInfo(audioResult = audioA, frameHashes = emptyList(), isVideo = false)

        comparator.computeSimilarity(videoNoAudio, audioOnly).shouldBeNull()
    }

    // --- video-no-audio comparisons ---

    @Test
    fun `video-no-audio + video-no-audio - uses multi-frame average`() {
        val a = MediaInfo(audioResult = null, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = null, frameHashes = framesB, isVideo = true)

        val sim = comparator.computeSimilarity(a, b)
        sim.shouldNotBeNull()
        sim shouldBe 1.0
    }

    @Test
    fun `video-no-audio + video-no-audio - below threshold returns null`() {
        val a = MediaInfo(audioResult = null, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = null, frameHashes = framesDifferent, isVideo = true)

        val sim = comparator.computeSimilarity(a, b)
        if (sim != null) {
            sim shouldBeLessThan MediaComparator.VISUAL_ONLY_THRESHOLD
        }
    }

    @Test
    fun `video-no-audio + empty frame hashes - returns null`() {
        val a = MediaInfo(audioResult = null, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = null, frameHashes = emptyList(), isVideo = true)

        comparator.computeSimilarity(a, b).shouldBeNull()
    }

    // --- no match possible ---

    @Test
    fun `no audio no frames - returns null`() {
        val a = MediaInfo(audioResult = null, frameHashes = emptyList(), isVideo = false)
        val b = MediaInfo(audioResult = null, frameHashes = emptyList(), isVideo = false)

        comparator.computeSimilarity(a, b).shouldBeNull()
    }

    // --- tiebreaker ---

    @Test
    fun `tiebreaker - both video with frames - weighted 70 30`() {
        val a = MediaInfo(audioResult = audioA, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHashes = framesB, isVideo = true)

        val audioSim = 0.90
        val result = comparator.computeWithTiebreaker(a, b, audioSim)

        result.shouldNotBeNull()
        // All frame hashes are identical so frameSim = 1.0
        // Expected: 0.90 * 0.7 + 1.0 * 0.3 = 0.93
        result shouldBe 0.90 * 0.7 + 1.0 * 0.3
    }

    @Test
    fun `tiebreaker - non-video returns null`() {
        val a = MediaInfo(audioResult = audioA, frameHashes = emptyList(), isVideo = false)
        val b = MediaInfo(audioResult = audioB, frameHashes = emptyList(), isVideo = false)

        comparator.computeWithTiebreaker(a, b, 0.90).shouldBeNull()
    }

    @Test
    fun `tiebreaker - empty frame hashes returns null`() {
        val a = MediaInfo(audioResult = audioA, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHashes = emptyList(), isVideo = true)

        comparator.computeWithTiebreaker(a, b, 0.90).shouldBeNull()
    }

    @Test
    fun `tiebreaker - one non-video returns null`() {
        val a = MediaInfo(audioResult = audioA, frameHashes = framesA, isVideo = true)
        val b = MediaInfo(audioResult = audioB, frameHashes = emptyList(), isVideo = false)

        comparator.computeWithTiebreaker(a, b, 0.90).shouldBeNull()
    }

    // --- averageFrameSimilarity ---

    @Test
    fun `averageFrameSimilarity - identical frames returns 1_0`() {
        comparator.averageFrameSimilarity(framesA, framesB) shouldBe 1.0
    }

    @Test
    fun `averageFrameSimilarity - empty list returns null`() {
        comparator.averageFrameSimilarity(emptyList(), framesA).shouldBeNull()
        comparator.averageFrameSimilarity(framesA, emptyList()).shouldBeNull()
    }

    @Test
    fun `averageFrameSimilarity - mismatched sizes uses minimum count`() {
        val short = frameHashes(0x1111L, 0x2222L)
        val long = frameHashes(0x1111L, 0x2222L, 0x3333L, 0x4444L, 0x5555L)
        // Should compare only 2 pairs
        comparator.averageFrameSimilarity(short, long) shouldBe 1.0
    }

    // --- multi-position audio fingerprint ---

    @Test
    fun `multi-position audio - identical 3 segments = 1_0`() {
        val seg = longArrayOf(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)
        val a = multiAudioResult(listOf(seg, seg, seg))
        val b = multiAudioResult(listOf(seg, seg, seg))

        a.similarityTo(b) shouldBe 1.0
    }

    @Test
    fun `multi-position audio - 3 segments vs 1 segment compares only first pair`() {
        val seg = longArrayOf(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)
        val diffSeg = longArrayOf(-1L, -1L, -1L, -1L)
        // a has 3 segments (first identical, rest different), b has 1 segment
        val a = multiAudioResult(listOf(seg, diffSeg, diffSeg))
        val b = multiAudioResult(listOf(seg))

        // Only first pair compared → 1.0
        a.similarityTo(b) shouldBe 1.0
    }

    @Test
    fun `multi-position audio - empty fingerprints returns 0_0`() {
        val empty = AudioFingerprinter.Result(fingerprints = emptyList(), durationMs = 10000L)
        val nonEmpty = multiAudioResult(
            listOf(longArrayOf(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)),
        )

        empty.similarityTo(nonEmpty) shouldBe 0.0
        nonEmpty.similarityTo(empty) shouldBe 0.0
    }

    @Test
    fun `multi-position audio - one different segment lowers average`() {
        val seg = longArrayOf(0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L, 0x4444444444444444L)
        val diffSeg = longArrayOf(-1L, -1L, -1L, -1L)
        val a = multiAudioResult(listOf(seg, seg, seg))
        val b = multiAudioResult(listOf(seg, seg, diffSeg))

        val sim = a.similarityTo(b)
        // 2 perfect pairs + 1 imperfect → average < 1.0
        sim shouldBeLessThan 1.0
    }
}

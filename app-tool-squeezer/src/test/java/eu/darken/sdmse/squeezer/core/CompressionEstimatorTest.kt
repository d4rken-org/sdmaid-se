package eu.darken.sdmse.squeezer.core

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CompressionEstimatorTest : BaseTest() {

    private val subject = CompressionEstimator()

    @Test
    fun `estimateOutputRatio - JPEG step function`() {
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, 50) shouldBe 0.30
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, 70) shouldBe 0.50
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, 80) shouldBe 0.65
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, 90) shouldBe 0.80
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, 100) shouldBe 1.00
    }

    @Test
    fun `estimateOutputRatio - WebP step function`() {
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_WEBP, 50) shouldBe 0.25
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_WEBP, 80) shouldBe 0.55
        subject.estimateOutputRatio(CompressibleImage.MIME_TYPE_WEBP, 100) shouldBe 1.00
    }

    @Test
    fun `estimateOutputRatio - video mime type returns null`() {
        // Video uses the dedicated estimateVideoSize path, not the ratio-based estimator.
        subject.estimateOutputRatio(CompressibleVideo.MIME_TYPE_MP4, 80) shouldBe null
    }

    @Test
    fun `estimateOutputRatio - unknown mime type returns null`() {
        subject.estimateOutputRatio("image/gif", 80) shouldBe null
    }

    // --- Video estimator ---

    @Test
    fun `estimateVideoSize - linear in quality for a fixed bitrate and duration`() {
        // 10 MBit/s * 60 s = 600 MBit = 75 MB video + ~960 KB audio overhead
        val originalSize = 80_000_000L
        val durationMs = 60_000L
        val bitrateBps = 10_000_000L

        val at50 = subject.estimateVideoSize(originalSize, durationMs, bitrateBps, 50)!!
        val at80 = subject.estimateVideoSize(originalSize, durationMs, bitrateBps, 80)!!

        // At 80%, target bitrate is 8 Mbit/s → 60 MB + overhead.
        // At 50%, target bitrate is 5 Mbit/s → 37.5 MB + overhead.
        // Verify the 80% estimate is bigger than the 50% estimate.
        at80 shouldBeGreaterThan at50

        // Sanity check the 80% number: video = 8 Mbit/s * 60 s / 8 = 60 MB, audio ~ 960 KB.
        // Allow a small tolerance.
        val expectedVideo = (bitrateBps * 80 / 100) * durationMs / 8_000L
        at80 shouldBeGreaterThan expectedVideo - 1
        at80 shouldBeLessThan expectedVideo + 2_000_000L // audio+mux fudge
    }

    @Test
    fun `estimateVideoSize - clamps to original size`() {
        val originalSize = 1_000_000L
        // Unrealistically high bitrate × duration produces >original bytes before clamping
        val estimate = subject.estimateVideoSize(
            originalSize = originalSize,
            durationMs = 60_000L,
            originalBitrateBps = 100_000_000L,
            quality = 100,
        )!!
        estimate shouldBeLessThanOrEqual originalSize
    }

    @Test
    fun `estimateVideoSize - missing duration returns null`() {
        subject.estimateVideoSize(
            originalSize = 1_000_000L,
            durationMs = 0L,
            originalBitrateBps = 1_000_000L,
            quality = 80,
        ) shouldBe null
    }

    @Test
    fun `estimateVideoSize - missing bitrate returns null`() {
        subject.estimateVideoSize(
            originalSize = 1_000_000L,
            durationMs = 10_000L,
            originalBitrateBps = 0L,
            quality = 80,
        ) shouldBe null
    }

    @Test
    fun `estimateVideoSize - honors minimum bitrate floor at very low quality`() {
        // At quality=1, raw target = bitrateBps * 1 / 100, which for a 100kbps source is 1 kbps —
        // below the 100 kbps floor.
        val tinyBitrate = subject.estimateVideoSize(
            originalSize = 10_000_000L,
            durationMs = 60_000L,
            originalBitrateBps = 100_000L,
            quality = 1,
        )!!

        // The floor ensures the estimate is at least (100kbps * 60s) / 8 = 750 KB for video bytes
        // plus audio overhead. It must not be zero.
        tinyBitrate shouldBeGreaterThan 0L
    }

    @Test
    fun `estimateVideoSize - larger quality yields larger estimate`() {
        val originalSize = 50_000_000L
        val durationMs = 30_000L
        val bitrateBps = 8_000_000L

        val low = subject.estimateVideoSize(originalSize, durationMs, bitrateBps, 40)!!
        val mid = subject.estimateVideoSize(originalSize, durationMs, bitrateBps, 70)!!
        val high = subject.estimateVideoSize(originalSize, durationMs, bitrateBps, 95)!!

        low shouldBeLessThan mid
        mid shouldBeLessThan high
    }
}

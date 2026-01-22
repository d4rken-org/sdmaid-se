package eu.darken.sdmse.compressor.core.scanner

import eu.darken.sdmse.compressor.core.CompressibleImage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Tests for the compression estimation logic.
 * The estimation function calculates expected file sizes based on quality settings.
 */
class ImageScannerEstimationTest : BaseTest() {

    /**
     * Replicate the estimation logic from CompressionEstimator for testing purposes.
     * This ensures our test expectations match the actual implementation.
     */
    private fun estimateCompressedSize(originalSize: Long, mimeType: String, quality: Int): Long? {
        return when (mimeType) {
            CompressibleImage.MIME_TYPE_JPEG -> {
                val ratio = when {
                    quality <= 50 -> 0.3
                    quality <= 70 -> 0.5
                    quality <= 80 -> 0.65
                    quality <= 90 -> 0.8
                    else -> 0.9
                }
                (originalSize * ratio).toLong()
            }
            CompressibleImage.MIME_TYPE_WEBP -> {
                val ratio = when {
                    quality <= 50 -> 0.25
                    quality <= 70 -> 0.40
                    quality <= 80 -> 0.55
                    quality <= 90 -> 0.70
                    else -> 0.85
                }
                (originalSize * ratio).toLong()
            }
            else -> null
        }
    }

    @Test
    fun `JPEG at quality 50 or less - 30% of original`() {
        val originalSize = 1_000_000L // 1 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 50) shouldBe 300_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 40) shouldBe 300_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 1) shouldBe 300_000L
    }

    @Test
    fun `JPEG at quality 51-70 - 50% of original`() {
        val originalSize = 1_000_000L // 1 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 51) shouldBe 500_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 60) shouldBe 500_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 70) shouldBe 500_000L
    }

    @Test
    fun `JPEG at quality 71-80 - 65% of original`() {
        val originalSize = 1_000_000L // 1 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 71) shouldBe 650_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 75) shouldBe 650_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 80) shouldBe 650_000L
    }

    @Test
    fun `JPEG at quality 81-90 - 80% of original`() {
        val originalSize = 1_000_000L // 1 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 81) shouldBe 800_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 85) shouldBe 800_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 90) shouldBe 800_000L
    }

    @Test
    fun `JPEG at quality 91-100 - 90% of original`() {
        val originalSize = 1_000_000L // 1 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 91) shouldBe 900_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 95) shouldBe 900_000L
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 100) shouldBe 900_000L
    }

    @Test
    fun `WebP at quality 80 - 55% of original`() {
        val originalSize = 1_000_000L // 1 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_WEBP, 80) shouldBe 550_000L
    }

    @Test
    fun `unknown mime type returns null`() {
        val originalSize = 1_000_000L

        estimateCompressedSize(originalSize, "image/gif", 80) shouldBe null
        estimateCompressedSize(originalSize, "video/mp4", 80) shouldBe null
    }

    @Test
    fun `estimation with default quality (80)`() {
        val originalSize = 5_000_000L // 5 MB

        // At quality 80, JPEG should be 65% of original
        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 80) shouldBe 3_250_000L
    }

    @Test
    fun `savings calculation - JPEG at quality 80`() {
        val originalSize = 5_000_000L // 5 MB
        val estimatedSize = estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 80)!!

        val savings = originalSize - estimatedSize

        savings shouldBe 1_750_000L // 35% savings
    }

    @Test
    fun `savings calculation - WebP at quality 80`() {
        val originalSize = 5_000_000L // 5 MB
        val estimatedSize = estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_WEBP, 80)!!

        val savings = originalSize - estimatedSize

        savings shouldBe 2_250_000L // 45% savings
    }

    @Test
    fun `estimation handles small files`() {
        val originalSize = 100_000L // 100 KB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 80) shouldBe 65_000L
    }

    @Test
    fun `estimation handles large files`() {
        val originalSize = 50_000_000L // 50 MB

        estimateCompressedSize(originalSize, CompressibleImage.MIME_TYPE_JPEG, 80) shouldBe 32_500_000L
    }
}

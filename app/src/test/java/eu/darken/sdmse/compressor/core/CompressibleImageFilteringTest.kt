package eu.darken.sdmse.compressor.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

class CompressibleImageFilteringTest : BaseTest() {

    private fun createImage(
        path: String,
        size: Long = 1024 * 1024L,
        mimeType: String = CompressibleImage.MIME_TYPE_JPEG,
        modifiedAt: Instant = Instant.now(),
        wasCompressedBefore: Boolean = false,
    ) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = modifiedAt,
            target = null,
        ),
        mimeType = mimeType,
        wasCompressedBefore = wasCompressedBefore,
    )

    // === MIME Type Filtering Tests ===

    @Test
    fun `filter by JPEG mime type`() {
        val images = listOf(
            createImage("/img1.jpg", mimeType = CompressibleImage.MIME_TYPE_JPEG),
            createImage("/img3.webp", mimeType = CompressibleImage.MIME_TYPE_WEBP),
        )

        val jpegOnly = images.filter { it.isJpeg }

        jpegOnly shouldHaveSize 1
        jpegOnly.first().mimeType shouldBe CompressibleImage.MIME_TYPE_JPEG
    }

    @Test
    fun `filter by WebP mime type`() {
        val images = listOf(
            createImage("/img1.jpg", mimeType = CompressibleImage.MIME_TYPE_JPEG),
            createImage("/img3.webp", mimeType = CompressibleImage.MIME_TYPE_WEBP),
        )

        val webpOnly = images.filter { it.isWebp }

        webpOnly shouldHaveSize 1
        webpOnly.first().mimeType shouldBe CompressibleImage.MIME_TYPE_WEBP
    }

    @Test
    fun `filter by enabled mime types set`() {
        val images = listOf(
            createImage("/img1.jpg", mimeType = CompressibleImage.MIME_TYPE_JPEG),
            createImage("/img3.webp", mimeType = CompressibleImage.MIME_TYPE_WEBP),
            createImage("/img4.jpg", mimeType = CompressibleImage.MIME_TYPE_JPEG),
        )

        val enabledTypes = setOf(CompressibleImage.MIME_TYPE_JPEG, CompressibleImage.MIME_TYPE_WEBP)
        val filtered = images.filter { it.mimeType in enabledTypes }

        filtered shouldHaveSize 3
    }

    // === Size Filtering Tests ===

    @Test
    fun `filter by minimum size - excludes small files`() {
        val minSize = 512 * 1024L // 512KB

        val images = listOf(
            createImage("/small.jpg", size = 100 * 1024L),  // 100KB - excluded
            createImage("/medium.jpg", size = 512 * 1024L), // 512KB - included
            createImage("/large.jpg", size = 1024 * 1024L), // 1MB - included
        )

        val filtered = images.filter { it.size >= minSize }

        filtered shouldHaveSize 2
        filtered.none { it.size < minSize } shouldBe true
    }

    @Test
    fun `filter at exact minimum size boundary`() {
        val minSize = 512 * 1024L

        val images = listOf(
            createImage("/at_boundary.jpg", size = 512 * 1024L),
            createImage("/below_boundary.jpg", size = 512 * 1024L - 1),
        )

        val filtered = images.filter { it.size >= minSize }

        filtered shouldHaveSize 1
        filtered.first().size shouldBe 512 * 1024L
    }

    // === Age Filtering Tests ===

    @Test
    fun `filter by max age - excludes old files`() {
        val maxAgeDays = 7
        val cutoff = Instant.now().minus(Duration.ofDays(maxAgeDays.toLong()))

        val images = listOf(
            createImage("/recent.jpg", modifiedAt = Instant.now()),
            createImage("/within_range.jpg", modifiedAt = Instant.now().minus(Duration.ofDays(5))),
            createImage("/old.jpg", modifiedAt = Instant.now().minus(Duration.ofDays(10))),
        )

        val filtered = images.filter { !it.modifiedAt.isBefore(cutoff) }

        filtered shouldHaveSize 2
    }

    @Test
    fun `filter with null max age - includes all ages`() {
        val maxAgeDays: Int? = null

        val images = listOf(
            createImage("/recent.jpg", modifiedAt = Instant.now()),
            createImage("/very_old.jpg", modifiedAt = Instant.EPOCH),
        )

        // When maxAgeDays is null, no age filtering should be applied
        val filtered = if (maxAgeDays != null) {
            val cutoff = Instant.now().minus(Duration.ofDays(maxAgeDays.toLong()))
            images.filter { !it.modifiedAt.isBefore(cutoff) }
        } else {
            images
        }

        filtered shouldHaveSize 2
    }

    // === Previously Compressed Filtering Tests ===

    @Test
    fun `filter excludes previously compressed images`() {
        val images = listOf(
            createImage("/new.jpg", wasCompressedBefore = false),
            createImage("/already_done.jpg", wasCompressedBefore = true),
            createImage("/another_new.jpg", wasCompressedBefore = false),
        )

        val filtered = images.filter { !it.wasCompressedBefore }

        filtered shouldHaveSize 2
        filtered.all { !it.wasCompressedBefore } shouldBe true
    }

    @Test
    fun `filter includes previously compressed when skip is disabled`() {
        val skipPreviouslyCompressed = false

        val images = listOf(
            createImage("/new.jpg", wasCompressedBefore = false),
            createImage("/already_done.jpg", wasCompressedBefore = true),
        )

        val filtered = if (skipPreviouslyCompressed) {
            images.filter { !it.wasCompressedBefore }
        } else {
            images
        }

        filtered shouldHaveSize 2
    }

    // === Combined Filtering Tests ===

    @Test
    fun `combined filtering - JPEG, minimum size, not compressed before`() {
        val minSize = 512 * 1024L

        val images = listOf(
            createImage("/good.jpg", size = 1024 * 1024L, mimeType = CompressibleImage.MIME_TYPE_JPEG, wasCompressedBefore = false),
            createImage("/too_small.jpg", size = 100 * 1024L, mimeType = CompressibleImage.MIME_TYPE_JPEG, wasCompressedBefore = false),
            createImage("/webp_type.webp", size = 1024 * 1024L, mimeType = CompressibleImage.MIME_TYPE_WEBP, wasCompressedBefore = false),
            createImage("/already_compressed.jpg", size = 1024 * 1024L, mimeType = CompressibleImage.MIME_TYPE_JPEG, wasCompressedBefore = true),
        )

        val filtered = images
            .filter { it.isJpeg }
            .filter { it.size >= minSize }
            .filter { !it.wasCompressedBefore }

        filtered shouldHaveSize 1
        filtered.first().path.path shouldBe "/good.jpg"
    }

    // === SUPPORTED_MIME_TYPES Tests ===

    @Test
    fun `SUPPORTED_MIME_TYPES contains all expected types`() {
        CompressibleImage.SUPPORTED_MIME_TYPES shouldContainAll listOf(
            CompressibleImage.MIME_TYPE_JPEG,
            CompressibleImage.MIME_TYPE_WEBP,
        )
    }

    @Test
    fun `SUPPORTED_MIME_TYPES has exactly 2 types`() {
        CompressibleImage.SUPPORTED_MIME_TYPES shouldHaveSize 2
    }

    @Test
    fun `filtering by SUPPORTED_MIME_TYPES excludes unsupported types`() {
        val images = listOf(
            createImage("/img1.jpg", mimeType = CompressibleImage.MIME_TYPE_JPEG),
            createImage("/img2.gif", mimeType = "image/gif"),  // Not supported
            createImage("/img3.bmp", mimeType = "image/bmp"),  // Not supported
        )

        val filtered = images.filter { it.mimeType in CompressibleImage.SUPPORTED_MIME_TYPES }

        filtered shouldHaveSize 1
    }

    // === Compressor.Data Extension Tests ===

    @Test
    fun `hasData returns true when images exist`() {
        val data = Compressor.Data(
            images = setOf(createImage("/test.jpg"))
        )

        data.hasData shouldBe true
    }

    @Test
    fun `hasData returns false when images is empty`() {
        val data = Compressor.Data(images = emptySet())

        data.hasData shouldBe false
    }

    @Test
    fun `hasData returns false for null data`() {
        val data: Compressor.Data? = null

        data.hasData shouldBe false
    }

    // === Estimated Savings Filtering ===

    @Test
    fun `filter images with estimated savings`() {
        val images = listOf(
            createImage("/has_savings.jpg").copy(estimatedCompressedSize = 500_000L),
            createImage("/no_estimate.jpg"),  // null estimatedCompressedSize
        )

        val withSavings = images.filter { it.estimatedSavings != null }

        withSavings shouldHaveSize 1
    }

    @Test
    fun `filter images with positive estimated savings`() {
        val img1 = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("/good_savings.jpg")),
                fileType = FileType.FILE,
                size = 1_000_000L,
                modifiedAt = Instant.now(),
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
            estimatedCompressedSize = 650_000L, // 35% savings
        )

        val img2 = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("/no_savings.jpg")),
                fileType = FileType.FILE,
                size = 100_000L,
                modifiedAt = Instant.now(),
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
            estimatedCompressedSize = 100_000L, // 0% savings
        )

        val images = listOf(img1, img2)
        val withPositiveSavings = images.filter { (it.estimatedSavings ?: 0L) > 0 }

        withPositiveSavings shouldHaveSize 1
        withPositiveSavings.first().estimatedSavings shouldBe 350_000L
    }
}

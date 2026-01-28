package eu.darken.sdmse.compressor.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class CompressorTest : BaseTest() {

    private fun createImage(
        path: String,
        size: Long = 1024 * 1024L,
        estimatedCompressedSize: Long? = null,
        wasCompressedBefore: Boolean = false,
    ) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
        estimatedCompressedSize = estimatedCompressedSize,
        wasCompressedBefore = wasCompressedBefore,
    )

    @Test
    fun `totalSize - calculates sum of all image sizes`() {
        val data = Compressor.Data(
            images = setOf(
                createImage("img1.jpg", size = 1000L),
                createImage("img2.jpg", size = 2000L),
                createImage("img3.jpg", size = 3000L),
            )
        )

        data.totalSize shouldBe 6000L
    }

    @Test
    fun `totalSize - empty set returns zero`() {
        val data = Compressor.Data(images = emptySet())
        data.totalSize shouldBe 0L
    }

    @Test
    fun `totalCount - returns correct count`() {
        val data = Compressor.Data(
            images = setOf(
                createImage("img1.jpg"),
                createImage("img2.jpg"),
            )
        )

        data.totalCount shouldBe 2
    }

    @Test
    fun `estimatedSavings - calculates sum of all estimated savings`() {
        val data = Compressor.Data(
            images = setOf(
                createImage("img1.jpg", size = 1000L, estimatedCompressedSize = 800L),
                createImage("img2.jpg", size = 2000L, estimatedCompressedSize = 1500L),
                createImage("img3.jpg", size = 3000L, estimatedCompressedSize = 2000L),
            )
        )

        // Savings: (1000-800) + (2000-1500) + (3000-2000) = 200 + 500 + 1000 = 1700
        data.estimatedSavings shouldBe 1700L
    }

    @Test
    fun `estimatedSavings - handles null estimated sizes`() {
        val data = Compressor.Data(
            images = setOf(
                createImage("img1.jpg", size = 1000L, estimatedCompressedSize = 800L),
                createImage("img2.jpg", size = 2000L, estimatedCompressedSize = null),
            )
        )

        // Only img1 has estimated savings: 1000-800 = 200
        data.estimatedSavings shouldBe 200L
    }

    @Test
    fun `prune - removes processed images`() {
        val img1 = createImage("img1.jpg")
        val img2 = createImage("img2.jpg")
        val img3 = createImage("img3.jpg")

        val original = Compressor.Data(
            images = setOf(img1, img2, img3)
        )

        val processedIds = setOf(img1.identifier, img3.identifier)

        val pruned = original.prune(processedIds)

        pruned.images.size shouldBe 1
        pruned.images.first().identifier shouldBe img2.identifier
    }

    @Test
    fun `prune - removes all when all processed`() {
        val img1 = createImage("img1.jpg")
        val img2 = createImage("img2.jpg")

        val original = Compressor.Data(images = setOf(img1, img2))

        val processedIds = setOf(img1.identifier, img2.identifier)

        val pruned = original.prune(processedIds)

        pruned.images shouldBe emptySet()
    }

    @Test
    fun `prune - handles empty processed set`() {
        val img1 = createImage("img1.jpg")
        val img2 = createImage("img2.jpg")

        val original = Compressor.Data(images = setOf(img1, img2))

        val pruned = original.prune(emptySet())

        pruned.images.size shouldBe 2
    }

    @Test
    fun `CompressibleImage - estimatedSavings calculation`() {
        val image = createImage("test.jpg", size = 1000L, estimatedCompressedSize = 650L)

        image.estimatedSavings shouldBe 350L
    }

    @Test
    fun `CompressibleImage - estimatedSavings with null compressed size`() {
        val image = createImage("test.jpg", size = 1000L, estimatedCompressedSize = null)

        image.estimatedSavings shouldBe null
    }

    @Test
    fun `CompressibleImage - estimatedSavings coerces to at least zero`() {
        // Edge case: if estimated compressed size is somehow larger than original
        val image = createImage("test.jpg", size = 1000L, estimatedCompressedSize = 1500L)

        image.estimatedSavings shouldBe 0L
    }

    @Test
    fun `CompressibleImage - isJpeg detection`() {
        val jpegImage = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("test.jpg")),
                fileType = FileType.FILE,
                size = 1000L,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
        )

        jpegImage.isJpeg shouldBe true
        jpegImage.isWebp shouldBe false
    }

    @Test
    fun `CompressibleImage - isWebp detection`() {
        val webpImage = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("test.webp")),
                fileType = FileType.FILE,
                size = 1000L,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_WEBP,
        )

        webpImage.isJpeg shouldBe false
        webpImage.isWebp shouldBe true
    }
}

package eu.darken.sdmse.compressor.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class CompressibleImageIdentifierTest : BaseTest() {

    private fun createImage(
        path: String,
        size: Long = 1024 * 1024L,
    ) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
    )

    @Test
    fun `same path produces same identifier`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val image1 = createImage(path)
        val image2 = createImage(path)

        image1.identifier shouldBe image2.identifier
    }

    @Test
    fun `different paths produce different identifiers`() {
        val image1 = createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val image2 = createImage("/storage/emulated/0/DCIM/Camera/IMG_002.jpg")

        image1.identifier shouldNotBe image2.identifier
    }

    @Test
    fun `identifier value is derived from path`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val image = createImage(path)

        image.identifier.value shouldBe path
    }

    @Test
    fun `identifier works correctly in Set`() {
        val image1 = createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val image2 = createImage("/storage/emulated/0/DCIM/Camera/IMG_002.jpg")
        val image3 = createImage("/storage/emulated/0/DCIM/Camera/IMG_003.jpg")

        val idSet = setOf(image1.identifier, image2.identifier, image3.identifier)

        idSet shouldHaveSize 3
    }

    @Test
    fun `duplicate identifiers collapse in Set`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val image1 = createImage(path)
        val image2 = createImage(path)

        val idSet = setOf(image1.identifier, image2.identifier)

        idSet shouldHaveSize 1
    }

    @Test
    fun `identifier works correctly as Map key`() {
        val image1 = createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg")
        val image2 = createImage("/storage/emulated/0/DCIM/Camera/IMG_002.jpg")

        val map = mapOf(
            image1.identifier to "first",
            image2.identifier to "second",
        )

        map[image1.identifier] shouldBe "first"
        map[image2.identifier] shouldBe "second"
    }

    @Test
    fun `identifier lookup in Map works with same path`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        val image1 = createImage(path)
        val image2 = createImage(path)

        val map = mapOf(image1.identifier to "stored_value")

        // Lookup with different instance but same path should find the value
        map[image2.identifier] shouldBe "stored_value"
    }

    @Test
    fun `CompressibleMedia Id data class equality`() {
        val id1 = CompressibleMedia.Id("/test/path.jpg")
        val id2 = CompressibleMedia.Id("/test/path.jpg")

        id1 shouldBe id2
        id1.hashCode() shouldBe id2.hashCode()
    }

    @Test
    fun `CompressibleMedia Id data class inequality`() {
        val id1 = CompressibleMedia.Id("/test/path1.jpg")
        val id2 = CompressibleMedia.Id("/test/path2.jpg")

        id1 shouldNotBe id2
    }

    @Test
    fun `identifier is stable across image property changes`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"

        val smallImage = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File(path)),
                fileType = FileType.FILE,
                size = 100L,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
        )

        val largeImage = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File(path)),
                fileType = FileType.FILE,
                size = 10_000_000L,
                modifiedAt = Instant.now(),
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
        )

        // Same path = same identifier, regardless of other properties
        smallImage.identifier shouldBe largeImage.identifier
    }

    @Test
    fun `identifier with special characters in path`() {
        val path = "/storage/emulated/0/DCIM/Camera/Photo 2024-01-01 12:00:00.jpg"
        val image = createImage(path)

        image.identifier.value shouldBe path
    }

    @Test
    fun `identifier with unicode characters in path`() {
        val path = "/storage/emulated/0/DCIM/相机/照片.jpg"
        val image = createImage(path)

        image.identifier.value shouldBe path
    }

    @Test
    fun `identifiers can be used to filter images from a set`() {
        val img1 = createImage("/img1.jpg")
        val img2 = createImage("/img2.jpg")
        val img3 = createImage("/img3.jpg")

        val allImages = setOf(img1, img2, img3)
        val processedIds = setOf(img1.identifier, img3.identifier)

        val remaining = allImages.filter { !processedIds.contains(it.identifier) }

        remaining shouldHaveSize 1
        remaining.first().identifier shouldBe img2.identifier
    }

    @Test
    fun `identifiers preserve equality when stored and retrieved`() {
        val image = createImage("/storage/emulated/0/test.jpg")
        val originalId = image.identifier

        // Store in a collection
        val storedIds = mutableSetOf<CompressibleMedia.Id>()
        storedIds.add(originalId)

        // Create new identifier with same path
        val lookupId = CompressibleMedia.Id("/storage/emulated/0/test.jpg")

        storedIds.contains(lookupId) shouldBe true
    }

    @Test
    fun `different MIME types same path have same identifier`() {
        val path = "/storage/emulated/0/DCIM/test"

        val jpegImage = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File(path)),
                fileType = FileType.FILE,
                size = 1000L,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
        )

        val webpImage = CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File(path)),
                fileType = FileType.FILE,
                size = 1000L,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_WEBP,
        )

        // Identifier is based on path only, not MIME type
        jpegImage.identifier shouldBe webpImage.identifier
    }
}

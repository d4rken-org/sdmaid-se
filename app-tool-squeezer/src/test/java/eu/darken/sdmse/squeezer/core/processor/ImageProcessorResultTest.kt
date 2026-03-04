package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.squeezer.core.CompressibleImage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class ImageProcessorResultTest : BaseTest() {

    private fun createImage(
        path: String,
        size: Long = 1024 * 1024L,
        mimeType: String = CompressibleImage.MIME_TYPE_JPEG,
    ) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = mimeType,
    )

    @Test
    fun `empty result - no images processed`() {
        val result = ImageProcessor.Result(
            success = emptySet(),
            failed = emptyMap(),
            savedSpace = 0L,
        )

        result.success.shouldBeEmpty()
        result.savedSpace shouldBe 0L
    }

    @Test
    fun `single image processed successfully`() {
        val image = createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg", size = 5_000_000L)

        val result = ImageProcessor.Result(
            success = setOf(image),
            failed = emptyMap(),
            savedSpace = 1_750_000L, // 35% savings at quality 80
        )

        result.success.size shouldBe 1
        result.savedSpace shouldBe 1_750_000L
    }

    @Test
    fun `multiple images processed successfully`() {
        val images = setOf(
            createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg", size = 5_000_000L),
            createImage("/storage/emulated/0/DCIM/Camera/IMG_002.jpg", size = 3_000_000L),
            createImage("/storage/emulated/0/Pictures/photo.jpg", size = 2_000_000L),
        )

        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = 3_500_000L, // Combined savings
        )

        result.success.size shouldBe 3
        result.savedSpace shouldBe 3_500_000L
    }

    @Test
    fun `calculate processed count from success set`() {
        val images = setOf(
            createImage("/img1.jpg"),
            createImage("/img2.jpg"),
            createImage("/img3.jpg"),
            createImage("/img4.jpg"),
            createImage("/img5.jpg"),
        )

        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = 5_000_000L,
        )

        result.success.size shouldBe 5
    }

    @Test
    fun `zero saved space when no compression benefit`() {
        val image = createImage("/storage/emulated/0/DCIM/already_compressed.jpg", size = 100_000L)

        val result = ImageProcessor.Result(
            success = setOf(image),
            failed = emptyMap(),
            savedSpace = 0L, // No savings but still marked as processed
        )

        result.success.size shouldBe 1
        result.savedSpace shouldBe 0L
    }

    @Test
    fun `large saved space value`() {
        val images = (1..100).map { idx ->
            createImage("/storage/emulated/0/DCIM/Camera/IMG_$idx.jpg", size = 10_000_000L)
        }.toSet()

        // Simulating 35% savings on 100 images of 10MB each
        val savedSpace = 100 * 10_000_000L * 35 / 100 // 350 MB

        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = savedSpace,
        )

        result.success.size shouldBe 100
        result.savedSpace shouldBe 350_000_000L
    }

    @Test
    fun `result with different mime types`() {
        val images = setOf(
            createImage("/img1.jpg", mimeType = CompressibleImage.MIME_TYPE_JPEG),
            createImage("/img3.webp", mimeType = CompressibleImage.MIME_TYPE_WEBP),
        )

        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = 2_000_000L,
        )

        result.success.size shouldBe 2
        result.success.count { it.isJpeg } shouldBe 1
        result.success.count { it.isWebp } shouldBe 1
    }

    @Test
    fun `result data class equality`() {
        val image = createImage("/test.jpg")

        val result1 = ImageProcessor.Result(
            success = setOf(image),
            failed = emptyMap(),
            savedSpace = 500_000L,
        )

        val result2 = ImageProcessor.Result(
            success = setOf(image),
            failed = emptyMap(),
            savedSpace = 500_000L,
        )

        result1 shouldBe result2
    }

    @Test
    fun `result total original size calculation`() {
        val images = setOf(
            createImage("/img1.jpg", size = 5_000_000L),
            createImage("/img2.jpg", size = 3_000_000L),
            createImage("/img3.jpg", size = 2_000_000L),
        )

        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = 3_500_000L,
        )

        val totalOriginalSize = result.success.sumOf { it.size }
        totalOriginalSize shouldBe 10_000_000L
    }

    @Test
    fun `calculate compression ratio from result`() {
        val images = setOf(
            createImage("/img1.jpg", size = 10_000_000L),
        )

        val savedSpace = 3_500_000L // 35% savings
        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = savedSpace,
        )

        val totalOriginalSize = result.success.sumOf { it.size }
        val savingsPercent = (savedSpace.toDouble() / totalOriginalSize.toDouble() * 100).toInt()

        savingsPercent shouldBe 35
    }

    @Test
    fun `paths can be extracted from success set`() {
        val images = setOf(
            createImage("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"),
            createImage("/storage/emulated/0/DCIM/Camera/IMG_002.jpg"),
        )

        val result = ImageProcessor.Result(
            success = images,
            failed = emptyMap(),
            savedSpace = 1_000_000L,
        )

        val paths = result.success.map { it.path }.toSet()
        paths.size shouldBe 2
    }
}

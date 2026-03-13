package eu.darken.sdmse.squeezer.core.scanner

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

class ImageScannerOptionsTest : BaseTest() {

    @Test
    fun `default options with empty paths`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = SqueezerSettings.DEFAULT_QUALITY,
        )

        options.paths.shouldBeEmpty()
        options.minimumSize shouldBe 512 * 1024L
        options.minAge shouldBe null
        options.skipPreviouslyCompressed shouldBe true
        options.compressionQuality shouldBe 80
    }

    @Test
    fun `options with custom paths`() {
        val customPaths = setOf(
            LocalPath.build("/storage/emulated/0/DCIM"),
            LocalPath.build("/storage/emulated/0/Pictures"),
        )

        val options = ImageScanner.Options(
            paths = customPaths,
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.paths.size shouldBe 2
        options.paths shouldContainAll customPaths
    }

    @Test
    fun `enabled mime types - JPEG only`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.enabledMimeTypes.size shouldBe 1
        options.enabledMimeTypes shouldContain CompressibleImage.MIME_TYPE_JPEG
    }

    @Test
    fun `enabled mime types - JPEG and WebP`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(
                CompressibleImage.MIME_TYPE_JPEG,
                CompressibleImage.MIME_TYPE_WEBP,
            ),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.enabledMimeTypes.size shouldBe 2
        options.enabledMimeTypes shouldContain CompressibleImage.MIME_TYPE_JPEG
        options.enabledMimeTypes shouldContain CompressibleImage.MIME_TYPE_WEBP
    }

    @Test
    fun `enabled mime types - all supported`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = CompressibleImage.SUPPORTED_MIME_TYPES,
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.enabledMimeTypes shouldBe CompressibleImage.SUPPORTED_MIME_TYPES
        options.enabledMimeTypes.size shouldBe 2
    }

    @Test
    fun `minimum size boundary - at MIN_FILE_SIZE`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.minimumSize shouldBe 512 * 1024L // 512KB
    }

    @Test
    fun `minimum size - custom smaller value`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = 100 * 1024L, // 100KB
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.minimumSize shouldBe 100 * 1024L
    }

    @Test
    fun `minimum size - custom larger value`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = 1024 * 1024L, // 1MB
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.minimumSize shouldBe 1024 * 1024L
    }

    @Test
    fun `min age - null means no age limit`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.minAge shouldBe null
    }

    @Test
    fun `min age - 7 days`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = Duration.ofDays(7),
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.minAge shouldBe Duration.ofDays(7)
    }

    @Test
    fun `min age - 30 days`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = Duration.ofDays(30),
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.minAge shouldBe Duration.ofDays(30)
    }

    @Test
    fun `min age duration calculation - 7 days cutoff`() {
        val minAgeDays = 7
        val cutoff = Instant.now().minus(Duration.ofDays(minAgeDays.toLong()))

        cutoff shouldNotBe null
        // Cutoff should be approximately 7 days ago
        val daysBetween = Duration.between(cutoff, Instant.now()).toDays()
        daysBetween shouldBe 7L
    }

    @Test
    fun `quality constraint - minimum value 1`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 1,
        )

        options.compressionQuality shouldBe 1
    }

    @Test
    fun `quality constraint - maximum value 100`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 100,
        )

        options.compressionQuality shouldBe 100
    }

    @Test
    fun `quality constraint - default value 80`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = SqueezerSettings.DEFAULT_QUALITY,
        )

        options.compressionQuality shouldBe 80
    }

    @Test
    fun `skip previously compressed - enabled`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options.skipPreviouslyCompressed shouldBe true
    }

    @Test
    fun `skip previously compressed - disabled`() {
        val options = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = false,
            compressionQuality = 80,
        )

        options.skipPreviouslyCompressed shouldBe false
    }

    @Test
    fun `options data class equality`() {
        val options1 = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        val options2 = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options1 shouldBe options2
    }

    @Test
    fun `options data class inequality - different paths`() {
        val options1 = ImageScanner.Options(
            paths = emptySet(),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        val options2 = ImageScanner.Options(
            paths = setOf(LocalPath.build("/storage/emulated/0/DCIM")),
            minimumSize = SqueezerSettings.MIN_FILE_SIZE,
            minAge = null,
            enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
            skipPreviouslyCompressed = true,
            compressionQuality = 80,
        )

        options1 shouldNotBe options2
    }
}

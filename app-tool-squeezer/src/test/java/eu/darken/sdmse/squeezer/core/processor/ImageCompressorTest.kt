package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.squeezer.core.CompressibleImage
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.io.IOException

class ImageCompressorTest : BaseTest() {

    private val exifPreserver = mockk<ExifPreserver>(relaxed = true)
    private val encoderFactory = mockk<ImageEncoderFactory>(relaxed = true)
    private val heifExifExtractor = mockk<HeifExifExtractor>()

    private fun create() = ImageCompressor(
        exifPreserver = exifPreserver,
        encoderFactory = encoderFactory,
        heifExifExtractor = heifExifExtractor,
    )

    @Test
    fun `HEIC with unreadable EXIF aborts instead of silently stripping metadata`() {
        // The whole point of the HEIC EXIF path: when the source has an Exif item we can't extract
        // safely, compression must abort (so FileTransaction keeps the original) rather than encode
        // a metadata-less copy that quietly drops the user's date/location/camera tags.
        every { heifExifExtractor.extractExifBlock(any()) } returns
            HeifExifExtractor.Result.Unsupported("construction_method=2")

        shouldThrow<IOException> {
            create().compress(
                inputFile = File("build/tmp/does-not-need-to-exist.heic"),
                outputFile = File("build/tmp/out.heic"),
                mimeType = CompressibleImage.MIME_TYPE_HEIC,
                quality = 80,
                writeExifMarker = false,
            )
        }

        // Must abort BEFORE decoding/encoding — the encoder is never even selected.
        verify(exactly = 0) { encoderFactory.encoderFor(any()) }
    }
}

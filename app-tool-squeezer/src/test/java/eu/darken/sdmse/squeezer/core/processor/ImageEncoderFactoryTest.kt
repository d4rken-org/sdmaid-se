package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.squeezer.core.CompressibleImage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ImageEncoderFactoryTest : BaseTest() {

    private val bitmapCompressEncoder: BitmapCompressEncoder = mockk(relaxed = true)
    private val heifWriterEncoder: HeifWriterEncoder = mockk(relaxed = true)
    private val subject = ImageEncoderFactory(bitmapCompressEncoder, heifWriterEncoder)

    @Test
    fun `JPEG mime routes to BitmapCompressEncoder`() {
        subject.encoderFor(CompressibleImage.MIME_TYPE_JPEG) shouldBeSameInstanceAs bitmapCompressEncoder
    }

    @Test
    fun `WebP mime routes to BitmapCompressEncoder`() {
        subject.encoderFor(CompressibleImage.MIME_TYPE_WEBP) shouldBeSameInstanceAs bitmapCompressEncoder
    }

    @Test
    fun `HEIC mime routes to HeifWriterEncoder`() {
        subject.encoderFor(CompressibleImage.MIME_TYPE_HEIC) shouldBeSameInstanceAs heifWriterEncoder
    }

    @Test
    fun `HEIF mime routes to HeifWriterEncoder`() {
        subject.encoderFor(CompressibleImage.MIME_TYPE_HEIF) shouldBeSameInstanceAs heifWriterEncoder
    }
}

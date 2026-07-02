package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.MetadataPreservationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class ImageCompressorTest : BaseTest() {

    private val exifPreserver = mockk<ExifPreserver>(relaxed = true)
    private val encoderFactory = mockk<ImageEncoderFactory>(relaxed = true)
    private val heifExifExtractor = mockk<HeifExifExtractor>()
    private val heifTransformInspector = mockk<HeifTransformInspector>()

    private fun create() = ImageCompressor(
        exifPreserver = exifPreserver,
        encoderFactory = encoderFactory,
        heifExifExtractor = heifExifExtractor,
        heifTransformInspector = heifTransformInspector,
    )

    private fun compressHeic(inputFile: File = File("build/tmp/does-not-need-to-exist.heic")) = create().compress(
        inputFile = inputFile,
        outputFile = File("build/tmp/out.heic"),
        mimeType = CompressibleImage.MIME_TYPE_HEIC,
        quality = 80,
        writeExifMarker = false,
    )

    @Test
    fun `HEIC with unreadable EXIF aborts instead of silently stripping metadata`() {
        // The whole point of the HEIC EXIF path: when the source has an Exif item we can't extract
        // safely, compression must abort (so FileTransaction keeps the original) rather than encode
        // a metadata-less copy that quietly drops the user's date/location/camera tags.
        every { heifExifExtractor.extractExifBlock(any()) } returns
            HeifExifExtractor.Result.Unsupported("construction_method=2")
        every { heifTransformInspector.inspect(any()) } returns
            HeifTransformInspector.Result.NoTransform

        shouldThrow<MetadataPreservationException> { compressHeic() }

        // Must abort BEFORE decoding/encoding — the encoder is never even selected.
        verify(exactly = 0) { encoderFactory.encoderFor(any()) }
    }

    @Test
    fun `HEIC with a mirror transform aborts instead of dropping the mirror`() {
        // HeifWriter can't write imir; encoding anyway would produce a permanently mis-rendered
        // photo once the original is deleted.
        every { heifExifExtractor.extractExifBlock(any()) } returns HeifExifExtractor.Result.NoExif
        every { heifTransformInspector.inspect(any()) } returns
            HeifTransformInspector.Result.Mirrored

        shouldThrow<MetadataPreservationException> { compressHeic() }
        verify(exactly = 0) { encoderFactory.encoderFor(any()) }
    }

    @Test
    fun `HEIC with undeterminable transform state aborts`() {
        every { heifExifExtractor.extractExifBlock(any()) } returns HeifExifExtractor.Result.NoExif
        every { heifTransformInspector.inspect(any()) } returns
            HeifTransformInspector.Result.Unsupported("malformed ipco box")

        shouldThrow<MetadataPreservationException> { compressHeic() }
        verify(exactly = 0) { encoderFactory.encoderFor(any()) }
    }

    @Test
    fun `HEIC without transforms proceeds to the decode stage`() {
        // Sanity check that the transform gate lets clean files pass: with no container
        // transforms the flow reaches bitmap decoding (which fails on the JVM — that exact
        // failure proves we got past the metadata stage).
        every { heifExifExtractor.extractExifBlock(any()) } returns HeifExifExtractor.Result.NoExif
        every { heifTransformInspector.inspect(any()) } returns
            HeifTransformInspector.Result.NoTransform

        val input = File("build/tmp/exists-but-not-decodable.heic").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(64))
        }
        try {
            val thrown = runCatching { compressHeic(inputFile = input) }.exceptionOrNull()
            // Reaching BitmapFactory (unmocked on the JVM) proves the metadata gate passed the
            // file through instead of aborting.
            thrown.shouldBeInstanceOf<RuntimeException>()
            thrown.shouldNotBeInstanceOf<MetadataPreservationException>()
            (thrown.message ?: "") shouldContain "not mocked"
        } finally {
            input.delete()
        }
    }

    @Test
    fun `rotation decision - stored aspect preserved means rotation is propagated`() {
        // iPhone-style portrait: stored landscape 4032x3024 + irot; BitmapFactory returns the
        // stored (landscape) pixels -> carry the same rotation into the output.
        val decision = ImageCompressor.decideHeicRotation(
            transform = HeifTransformInspector.Result.Rotated(angleCcw = 1, storedWidth = 4032, storedHeight = 3024),
            decodedWidth = 4032,
            decodedHeight = 3024,
        )
        decision.shouldBeInstanceOf<ImageCompressor.RotationDecision.Propagate>().degreesCw shouldBe 270

        ImageCompressor.decideHeicRotation(
            transform = HeifTransformInspector.Result.Rotated(angleCcw = 3, storedWidth = 1280, storedHeight = 720),
            // Sampled decode: aspect still landscape.
            decodedWidth = 640,
            decodedHeight = 360,
        ).shouldBeInstanceOf<ImageCompressor.RotationDecision.Propagate>().degreesCw shouldBe 90
    }

    @Test
    fun `rotation decision - swapped aspect means the decoder baked the rotation - skip`() {
        val decision = ImageCompressor.decideHeicRotation(
            transform = HeifTransformInspector.Result.Rotated(angleCcw = 1, storedWidth = 4032, storedHeight = 3024),
            decodedWidth = 3024,
            decodedHeight = 4032,
        )
        decision.shouldBeInstanceOf<ImageCompressor.RotationDecision.Skip>()
    }

    @Test
    fun `rotation decision - unverifiable cases are skipped`() {
        // 180°: dimensions can't reveal whether the decoder baked it.
        ImageCompressor.decideHeicRotation(
            transform = HeifTransformInspector.Result.Rotated(angleCcw = 2, storedWidth = 4032, storedHeight = 3024),
            decodedWidth = 4032,
            decodedHeight = 3024,
        ).shouldBeInstanceOf<ImageCompressor.RotationDecision.Skip>()

        // Square source: same problem.
        ImageCompressor.decideHeicRotation(
            transform = HeifTransformInspector.Result.Rotated(angleCcw = 1, storedWidth = 2048, storedHeight = 2048),
            decodedWidth = 2048,
            decodedHeight = 2048,
        ).shouldBeInstanceOf<ImageCompressor.RotationDecision.Skip>()

        // Non-square source sampled down to a square decode: ambiguous, skip.
        ImageCompressor.decideHeicRotation(
            transform = HeifTransformInspector.Result.Rotated(angleCcw = 1, storedWidth = 1001, storedHeight = 1000),
            decodedWidth = 125,
            decodedHeight = 125,
        ).shouldBeInstanceOf<ImageCompressor.RotationDecision.Skip>()
    }
}

package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import eu.darken.sdmse.squeezer.core.CompressibleImage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BitmapCompressEncoderTest : BaseTest() {

    private lateinit var testDir: File
    private lateinit var subject: BitmapCompressEncoder

    @Before
    fun setup() {
        testDir = File(IO_TEST_BASEDIR, "bitmap_compress_encoder_test")
        testDir.mkdirs()
        subject = BitmapCompressEncoder()
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun `encode writes a non-empty JPEG`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val output = File(testDir, "out.jpg")

        subject.encode(bitmap, CompressibleImage.MIME_TYPE_JPEG, 80, output, null)

        output.length() shouldBeGreaterThan 0L
        bitmap.recycle()
    }

    @Test
    fun `encode writes a non-empty WebP`() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val output = File(testDir, "out.webp")

        subject.encode(bitmap, CompressibleImage.MIME_TYPE_WEBP, 80, output, null)

        output.length() shouldBeGreaterThan 0L
        bitmap.recycle()
    }

    @Test
    fun `encode throws IllegalArgumentException when called with HEIC mime`() {
        // BitmapCompressEncoder must never be reached for HEIC — the factory routes that to
        // HeifWriterEncoder. If something wires it up wrong, fail loudly.
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val output = File(testDir, "out.heic")

        shouldThrow<IllegalArgumentException> {
            subject.encode(bitmap, CompressibleImage.MIME_TYPE_HEIC, 80, output, null)
        }
        bitmap.recycle()
    }

    companion object {
        private const val IO_TEST_BASEDIR = "build/tmp/unit_tests"
    }
}

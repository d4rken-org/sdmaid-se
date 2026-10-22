package eu.darken.sdmse.common.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import eu.darken.sdmse.common.coil.ZoomImageRequests.MAX
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = TestApplication::class)
class ZoomImageRequestsTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tempDir: File = createTempDirectory("zoom-image-requests").toFile()
    private val imageLoader = ImageLoader.Builder(context)
        .diskCache(null)
        .memoryCache(null)
        .dispatcher(Dispatchers.Unconfined)
        .interceptorDispatcher(Dispatchers.Unconfined)
        .build()

    @After
    fun teardown() {
        imageLoader.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `request is capped with inexact precision and fit scale`() {
        val request = ImageRequest.Builder(context)
            .capZoomDecodeSize()
            .build()

        runBlocking { request.sizeResolver.size() } shouldBe Size(MAX, MAX)
        request.precision shouldBe Precision.INEXACT
        request.scale shouldBe Scale.FIT
    }

    @Test
    fun `cap fits RGBA_F16 under the RecordingCanvas limit`() {
        MAX.toLong() * MAX * 8 shouldBeLessThanOrEqual CANVAS_LIMIT
    }

    @Test
    fun `landscape photo is downsampled to the cap`() {
        assertCapped(5000, 3750)
    }

    @Test
    fun `portrait photo is downsampled to the cap`() {
        assertCapped(3750, 5000)
    }

    @Test
    fun `square photo is downsampled to the cap`() {
        assertCapped(4200, 4200)
    }

    @Test
    fun `panorama is downsampled to the cap`() {
        assertCapped(12000, 1500)
    }

    @Test
    fun `photo below the cap is not upscaled`() {
        val bitmap = decode(jpegFixture(3000, 2000))

        bitmap.width shouldBe 3000
        bitmap.height shouldBe 2000
    }

    @Test
    fun `high bit depth source decodes as RGBA_F16`() {
        val source = Bitmap.createBitmap(600, 400, Bitmap.Config.RGBA_F16).apply { eraseColor(Color.GREEN) }
        val file = File(tempDir, "f16.png")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()

        decode(file).config shouldBe Bitmap.Config.RGBA_F16
    }

    private fun assertCapped(srcWidth: Int, srcHeight: Int) {
        val bitmap = decode(jpegFixture(srcWidth, srcHeight))

        bitmap.width shouldBeLessThanOrEqual MAX
        bitmap.height shouldBeLessThanOrEqual MAX

        val longSide = max(bitmap.width, bitmap.height)
        val shortSide = min(bitmap.width, bitmap.height)
        abs(longSide - MAX) shouldBeLessThanOrEqual 1

        val expectedShortSide = longSide.toDouble() * min(srcWidth, srcHeight) / max(srcWidth, srcHeight)
        abs(shortSide - expectedShortSide) shouldBeLessThanOrEqual 1.0
        (bitmap.width >= bitmap.height) shouldBe (srcWidth >= srcHeight)

        bitmap.byteCount shouldBeLessThan 100 * 1024 * 1024
    }

    private fun jpegFixture(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val file = File(tempDir, "${width}x$height.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        return file
    }

    private fun decode(file: File): Bitmap = runBlocking {
        val request = ImageRequest.Builder(context)
            .data(file)
            .capZoomDecodeSize()
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request) as SuccessResult
        (result.drawable as BitmapDrawable).bitmap
    }

    companion object {
        private const val CANVAS_LIMIT = 100L * 1024 * 1024
    }
}

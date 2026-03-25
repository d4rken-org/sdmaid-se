package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PHashAlgorithmTest : BaseTest() {

    private fun createUniformBitmap(color: Int = Color.GRAY): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return bmp
    }

    private fun createCheckerboardBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        for (x in 0 until 64 step 8) {
            for (y in 0 until 64 step 8) {
                paint.color = if ((x / 8 + y / 8) % 2 == 0) Color.WHITE else Color.BLACK
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 8).toFloat(), (y + 8).toFloat(), paint)
            }
        }
        return bmp
    }

    @Test
    fun `uniform image has near-zero AC variance`() {
        val bmp = createUniformBitmap()
        val result = PHashAlgorithm().calc(bmp)
        result.acVariance shouldBeLessThan 1.0
        bmp.recycle()
    }

    @Test
    fun `different uniform colors both have near-zero AC variance`() {
        val gray = createUniformBitmap(Color.GRAY)
        val blue = createUniformBitmap(Color.BLUE)
        val resultGray = PHashAlgorithm().calc(gray)
        val resultBlue = PHashAlgorithm().calc(blue)
        resultGray.acVariance shouldBeLessThan 1.0
        resultBlue.acVariance shouldBeLessThan 1.0
        gray.recycle()
        blue.recycle()
    }

    @Test
    fun `patterned image has high AC variance`() {
        val bmp = createCheckerboardBitmap()
        val result = PHashAlgorithm().calc(bmp)
        result.acVariance shouldBeGreaterThan 100.0
        bmp.recycle()
    }

    @Test
    fun `patterned image produces 121-bit hash`() {
        val bmp = createCheckerboardBitmap()
        val result = PHashAlgorithm().calc(bmp)
        result.hash.size shouldBe 121
        bmp.recycle()
    }

    @Test
    fun `same image produces identical hashes`() {
        val bmp = createCheckerboardBitmap()
        val r1 = PHashAlgorithm().calc(bmp)
        val r2 = PHashAlgorithm().calc(bmp)
        r1.hash.similarityTo(r2.hash) shouldBe 1.0
        bmp.recycle()
    }

    @Test
    fun `algorithm works with different size parameters`() {
        val bmp = createCheckerboardBitmap()
        val small = PHashAlgorithm(size = 8, smallerSize = 4)
        val result = small.calc(bmp)
        // Verify hash has correct bit count: (4-1)^2 = 9
        result.hash.size shouldBe 9
        bmp.recycle()
    }
}

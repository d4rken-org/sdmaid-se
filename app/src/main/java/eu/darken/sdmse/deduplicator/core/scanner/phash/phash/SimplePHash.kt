package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * @See https://gist.github.com/kuFEAR/6e20342198d4040e0bb5
 *
 * pHash-like image hash.
 * Author: Elliot Shepherd (elliot@jarofworms.com
 * Based On: http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
 */
class SimplePHash constructor(
    private val size: Int = 32,
    private val smallerSize: Int = 8,
) {

    private val coefficients: DoubleArray = DoubleArray(size)

    private fun initCoefficients() {
        for (i in 1..<size) {
            coefficients[i] = 1.0
        }
        coefficients[0] = 1 / sqrt(2.0)
    }

    fun calc(img: Bitmap): Long {
        initCoefficients()

        /* 1. Reduce size.
        * Like Average Hash, pHash starts with a small image.
        * However, the image is larger than 8x8; 32x32 is a good size.
        * This is really done to simplify the DCT computation and not
        * because it is needed to reduce the high frequencies.
        */
        var workImg = resize(img, size, size)

        /* 2. Reduce color.
         * The image is reduced to a grayscale just to further simplify
         * the number of computations.
         */
        workImg = grayscale(workImg)

        val vals = Array(size) { DoubleArray(size) }
        for (x in 0..<workImg.width) {
            for (y in 0..<workImg.height) {
                vals[x][y] = getBlue(workImg, x, y).toDouble()
            }
        }

        /* 3. Compute the DCT.
         * The DCT separates the image into a collection of frequencies
         * and scalars. While JPEG uses an 8x8 DCT, this algorithm uses
         * a 32x32 DCT.
         */
        val dctVals = applyDCT(vals)

        /* 4. Reduce the DCT.
         * This is the magic step. While the DCT is 32x32, just keep the
         * top-left 8x8. Those represent the lowest frequencies in the
         * picture.
         */
        /* 5. Compute the average value.
         * Like the Average Hash, compute the mean DCT value (using only
         * the 8x8 DCT low-frequency values and excluding the first term
         * since the DC coefficient can be significantly different from
         * the other values and will throw off the average).
        */
        var total = 0.0
        for (x in 0..<smallerSize) {
            for (y in 0..<smallerSize) {
                total += dctVals[x][y]
            }
        }
        total -= dctVals[0][0]
        val avg = total / (smallerSize * smallerSize - 1).toDouble()

        /* 6. Further reduce the DCT.
         * This is the magic step. Set the 64 hash bits to 0 or 1
         * depending on whether each of the 64 DCT values is above or
         * below the average value. The result doesn't tell us the
         * actual low frequencies; it just tells us the very-rough
         * relative scale of the frequencies to the mean. The result
         * will not vary as long as the overall structure of the image
         * remains the same; this can survive gamma and color histogram
         * adjustments without a problem.
         */
        var hash: Long = 0
        for (x in 0..<smallerSize) {
            for (y in 0..<smallerSize) {
                if (x != 0 && y != 0) {
                    hash *= 2
                    if (dctVals[x][y] > avg) hash++
                }
            }
        }

        return hash
    }

    private fun resize(bm: Bitmap, newHeight: Int, newWidth: Int): Bitmap {
        return Bitmap.createScaledBitmap(bm, newWidth, newHeight, false)
    }

    private fun grayscale(orginalBitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        val colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
        val blackAndWhiteBitmap = orginalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val paint = Paint()
        paint.colorFilter = colorMatrixFilter
        val canvas = Canvas(blackAndWhiteBitmap)
        canvas.drawBitmap(blackAndWhiteBitmap, 0f, 0f, paint)
        return blackAndWhiteBitmap
    }

    private fun getBlue(img: Bitmap, x: Int, y: Int): Int = img.getPixel(x, y) and 0xff

    // From http://stackoverflow.com/questions/4240490/problems-with-dct-and-idct-algorithm-in-java
    private fun applyDCT(f: Array<DoubleArray>): Array<DoubleArray> {
        val result = Array(size) { DoubleArray(size) }
        for (u in 0..<size) {
            for (v in 0..<size) {
                var sum = 0.0
                for (i in 0..<size) {
                    for (j in 0..<size) {
                        val cos1 = cos((2 * i + 1) / (2.0 * size) * u * Math.PI)
                        val cos2 = cos((2 * j + 1) / (2.0 * size) * v * Math.PI)
                        sum += cos1 * cos2 * f[i][j]
                    }
                }
                sum *= coefficients[u] * coefficients[v] / 4.0
                result[u][v] = sum
            }
        }
        return result
    }
}
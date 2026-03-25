package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.get
import androidx.core.graphics.scale
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * @See https://gist.github.com/kuFEAR/6e20342198d4040e0bb5
 *
 * pHash-like image hash.
 * Author: Elliot Shepherd (elliot@jarofworms.com
 * Based On: http://www.hackerfactor.com/blog/index.php?/archives/432-Looks-Like-It.html
 */
class PHashAlgorithm constructor(
    private val size: Int = 64,
    private val smallerSize: Int = 12,
) {

    init {
        require(size > 1) { "size must be > 1, was $size" }
        require(smallerSize in 2..size) { "smallerSize must be in 2..$size, was $smallerSize" }
    }

    data class Result(val hash: PHashBits, val acVariance: Double)

    fun calc(img: Bitmap): Result {
        // 1. Reduce size to NxN for DCT computation.
        var workImg = resize(img, size, size)

        // 2. Reduce color to grayscale.
        workImg = grayscale(workImg)

        val vals = Array(size) { DoubleArray(size) }
        for (x in 0..<workImg.width) {
            for (y in 0..<workImg.height) {
                vals[x][y] = getBlue(workImg, x, y).toDouble()
            }
        }

        // 3. Compute the DCT using separable 2-pass approach (O(n³) instead of O(n⁴)).
        val dctVals = applyDCT(vals)

        // 4. Compute mean and variance over the AC terms that contribute to the hash.
        // Only terms where x != 0 && y != 0 are used for hash bits.
        val acCount = (smallerSize - 1) * (smallerSize - 1)
        var acTotal = 0.0
        for (x in 1..<smallerSize) {
            for (y in 1..<smallerSize) {
                acTotal += dctVals[x][y]
            }
        }
        val avg = acTotal / acCount.toDouble()

        var sumSqDiff = 0.0
        for (x in 1..<smallerSize) {
            for (y in 1..<smallerSize) {
                val diff = dctVals[x][y] - avg
                sumSqDiff += diff * diff
            }
        }
        val acVariance = sumSqDiff / acCount.toDouble()

        // 5. Pack hash bits: 1 if DCT value > mean, 0 otherwise. MSB-first into LongArray.
        val totalBits = acCount
        val words = LongArray((totalBits + Long.SIZE_BITS - 1) / Long.SIZE_BITS)
        var bitIndex = 0
        for (x in 1..<smallerSize) {
            for (y in 1..<smallerSize) {
                if (dctVals[x][y] > avg) {
                    words[bitIndex / Long.SIZE_BITS] = words[bitIndex / Long.SIZE_BITS] or
                        (1L shl (Long.SIZE_BITS - 1 - bitIndex % Long.SIZE_BITS))
                }
                bitIndex++
            }
        }

        return Result(PHashBits(words, totalBits), acVariance)
    }

    private fun resize(bm: Bitmap, newHeight: Int, newWidth: Int): Bitmap {
        return bm.scale(newWidth, newHeight)
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

    private fun getBlue(img: Bitmap, x: Int, y: Int): Int = img[x, y] and 0xff

    // Precompute once per instance: table[i][u] = cos((2*i+1) / (2*n) * u * PI)
    private val cosTable: Array<DoubleArray> = Array(size) { i ->
        DoubleArray(size) { u ->
            cos((2 * i + 1) / (2.0 * size) * u * Math.PI)
        }
    }

    // Separable 2D DCT: two passes of 1D DCT (rows then columns).
    // O(n³) instead of the naive O(n⁴) approach.
    private fun applyDCT(f: Array<DoubleArray>): Array<DoubleArray> {
        val c0 = 1.0 / sqrt(2.0)

        // Pass 1: 1D DCT along columns for each row
        val temp = Array(size) { DoubleArray(size) }
        for (i in 0..<size) {
            for (v in 0..<size) {
                var sum = 0.0
                for (j in 0..<size) {
                    sum += cosTable[j][v] * f[i][j]
                }
                temp[i][v] = sum
            }
        }

        // Pass 2: 1D DCT along rows for each column
        val result = Array(size) { DoubleArray(size) }
        for (u in 0..<size) {
            for (v in 0..<size) {
                var sum = 0.0
                for (i in 0..<size) {
                    sum += cosTable[i][u] * temp[i][v]
                }
                val cu = if (u == 0) c0 else 1.0
                val cv = if (v == 0) c0 else 1.0
                result[u][v] = sum * cu * cv / 4.0
            }
        }
        return result
    }
}

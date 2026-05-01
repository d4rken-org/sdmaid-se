package eu.darken.sdmse.squeezer.ui.comparison

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import java.io.IOException

object BitmapSampler {

    private val TAG = logTag("Squeezer", "BitmapSampler")
    private const val MAX_DIMENSION = 2048

    fun decodeSampledBitmap(file: File, maxDimension: Int = MAX_DIMENSION): Bitmap? = try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        options.apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension)
        }

        file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    } catch (e: IOException) {
        log(TAG, WARN) { "Failed to decode bitmap from ${file.path}: ${e.message}" }
        null
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= maxDimension || (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

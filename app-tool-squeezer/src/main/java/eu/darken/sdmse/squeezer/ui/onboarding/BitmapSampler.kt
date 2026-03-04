package eu.darken.sdmse.squeezer.ui.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object BitmapSampler {

    private const val MAX_DIMENSION = 2048

    fun decodeSampledBitmap(file: File, maxDimension: Int = MAX_DIMENSION): Bitmap? {
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

        return file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
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

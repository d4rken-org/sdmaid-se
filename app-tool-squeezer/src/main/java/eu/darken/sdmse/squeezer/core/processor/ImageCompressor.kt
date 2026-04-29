package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.squeezer.core.CompressibleImage
import java.io.File
import javax.inject.Inject

class ImageCompressor @Inject constructor(
    private val exifPreserver: ExifPreserver,
) {

    fun compress(
        inputFile: File,
        outputFile: File,
        mimeType: String,
        quality: Int,
        writeExifMarker: Boolean,
    ) {
        val exifData = exifPreserver.extractExif(inputFile)

        val bitmap = decodeSampledBitmap(inputFile)
            ?: throw IllegalStateException("Failed to decode bitmap: ${inputFile.path}")
        try {
            compressBitmapToFile(bitmap, mimeType, quality, outputFile)
        } finally {
            bitmap.recycle()
        }

        // Fail-closed: if EXIF cannot be preserved, let the exception propagate so
        // FileTransaction aborts the replacement. A compressed file without original
        // EXIF metadata should not replace the source.
        if (exifData != null) {
            exifPreserver.applyExif(outputFile.absolutePath, exifData)
        }

        if (writeExifMarker) {
            exifPreserver.writeCompressionMarker(outputFile.absolutePath)
        }
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, MAX_DIMENSION)

        if (inSampleSize > 1) {
            log(TAG, VERBOSE) { "Using inSampleSize=$inSampleSize for ${options.outWidth}x${options.outHeight}" }
        }

        options.apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
        }

        return file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun compressBitmapToFile(
        bitmap: Bitmap,
        mimeType: String,
        quality: Int,
        outputFile: File,
    ) {
        val format = CompressibleImage.compressFormat(mimeType)
        outputFile.outputStream().buffered().use { output ->
            bitmap.compress(format, quality, output)
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

    companion object {
        internal const val MAX_DIMENSION = 4096
        private val TAG = logTag("Squeezer", "Image", "Compressor")
    }
}

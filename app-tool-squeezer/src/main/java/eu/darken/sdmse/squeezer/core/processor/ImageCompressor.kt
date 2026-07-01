package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.MetadataPreservationException
import java.io.File
import javax.inject.Inject

class ImageCompressor @Inject constructor(
    private val exifPreserver: ExifPreserver,
    private val encoderFactory: ImageEncoderFactory,
    private val heifExifExtractor: HeifExifExtractor,
) {

    fun compress(
        inputFile: File,
        outputFile: File,
        mimeType: String,
        quality: Int,
        writeExifMarker: Boolean,
    ) {
        val isHeic = mimeType in CompressibleImage.HEIC_MIME_TYPES

        // JPEG/WebP take EXIF via the post-encode ExifPreserver path; HEIC takes it as a
        // pre-built APP1 byte block embedded by HeifWriter.addExifData at encode time. We
        // bypass androidx.exifinterface for HEIF reads because it can't reliably parse
        // real-world HEIF EXIF — see HeifExifExtractor.
        val jpegWebpExif = if (isHeic) null else exifPreserver.extractExif(inputFile)
        val heicExifBlock: ByteArray? = if (isHeic) {
            when (val r = heifExifExtractor.extractExifBlock(inputFile)) {
                is HeifExifExtractor.Result.NoExif -> null
                is HeifExifExtractor.Result.Extracted -> r.bytes
                is HeifExifExtractor.Result.Unsupported -> throw MetadataPreservationException(
                    "HEIC ${inputFile.path} has unreadable EXIF metadata (${r.reason}); " +
                        "aborting to avoid silently stripping date/location/camera tags",
                )
            }
        } else {
            null
        }

        val bitmap = decodeSampledBitmap(inputFile)
            ?: throw IllegalStateException("Failed to decode bitmap: ${inputFile.path}")
        try {
            compressBitmapToFile(bitmap, mimeType, quality, outputFile, heicExifBlock)
        } finally {
            bitmap.recycle()
        }

        // Fail-closed: if EXIF cannot be preserved post-encode for JPEG/WebP, let the exception
        // propagate so FileTransaction aborts the replacement.
        if (jpegWebpExif != null) {
            exifPreserver.applyExif(outputFile.absolutePath, jpegWebpExif)
        }

        // EXIF compression marker only works on formats ExifInterface can write to.
        // HEIC re-compression is skip-protected via the content-hash in CompressionHistoryDatabase.
        if (writeExifMarker && !isHeic) {
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
        exifData: ByteArray?,
    ) {
        encoderFactory.encoderFor(mimeType).encode(bitmap, mimeType, quality, outputFile, exifData)
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

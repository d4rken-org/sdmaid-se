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
    private val heifTransformInspector: HeifTransformInspector,
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

        // The source's container rotation (irot) must be carried into the output: BitmapFactory
        // hands us the STORED pixels (verified on-device: it does not apply irot), and HeifWriter
        // writes no transform properties on its own — without this, every rotated HEIC (e.g.
        // iPhone portrait photos) would permanently lose its rotation.
        val heicTransform: HeifTransformInspector.Result? = if (isHeic) {
            when (val t = heifTransformInspector.inspect(inputFile)) {
                is HeifTransformInspector.Result.Mirrored -> throw MetadataPreservationException(
                    "HEIC ${inputFile.path} carries a mirror transform (imir) that the encoder " +
                        "cannot reproduce; aborting to avoid a mis-rendered result",
                )
                is HeifTransformInspector.Result.Unsupported -> throw MetadataPreservationException(
                    "HEIC ${inputFile.path} rotation state could not be determined (${t.reason}); " +
                        "aborting to avoid a mis-rotated result",
                )
                else -> t
            }
        } else {
            null
        }

        val bitmap = decodeSampledBitmap(inputFile)
            ?: throw IllegalStateException("Failed to decode bitmap: ${inputFile.path}")
        try {
            val rotationDegreesCw = when (heicTransform) {
                is HeifTransformInspector.Result.Rotated ->
                    when (val d = decideHeicRotation(heicTransform, bitmap.width, bitmap.height)) {
                        is RotationDecision.Propagate -> {
                            log(TAG, VERBOSE) {
                                "Carrying source rotation (${d.degreesCw}° cw) into ${outputFile.path}"
                            }
                            d.degreesCw
                        }
                        is RotationDecision.Skip -> throw MetadataPreservationException(
                            "HEIC ${inputFile.path}: ${d.reason}; aborting to avoid a mis-rotated result",
                        )
                    }
                else -> 0
            }
            compressBitmapToFile(bitmap, mimeType, quality, outputFile, heicExifBlock, rotationDegreesCw)
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
        rotationDegreesCw: Int,
    ) {
        encoderFactory.encoderFor(mimeType).encode(bitmap, mimeType, quality, outputFile, exifData, rotationDegreesCw)
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

    internal sealed class RotationDecision {
        data class Propagate(val degreesCw: Int) : RotationDecision()
        data class Skip(val reason: String) : RotationDecision()
    }

    companion object {
        internal const val MAX_DIMENSION = 4096
        private val TAG = logTag("Squeezer", "Image", "Compressor")

        /**
         * Decides how a source HEIC's irot is carried into the re-encoded output.
         *
         * On the decoder behavior we verified (BitmapFactory returns stored pixels, irot NOT
         * applied), propagating the same irot via HeifWriter.setRotation makes the output render
         * identically to the source in every viewer. The stored-vs-decoded aspect comparison
         * guards the one detectable case of a decoder that DID bake the 90°/270° rotation into
         * the pixels — that behavior is unverified in the wild, so such files are skipped, as
         * are the cases where baking would be undetectable (180°, square, sampled-to-square).
         */
        internal fun decideHeicRotation(
            transform: HeifTransformInspector.Result.Rotated,
            decodedWidth: Int,
            decodedHeight: Int,
        ): RotationDecision {
            if (transform.angleCcw == 2) {
                return RotationDecision.Skip("180° container rotation can't be verified against decoder behavior")
            }
            if (transform.storedWidth == transform.storedHeight) {
                return RotationDecision.Skip("square rotated image can't be verified against decoder behavior")
            }
            if (decodedWidth == decodedHeight) {
                return RotationDecision.Skip("sampled decode is square; rotation can't be verified")
            }
            val storedLandscape = transform.storedWidth > transform.storedHeight
            val decodedLandscape = decodedWidth > decodedHeight
            return if (storedLandscape == decodedLandscape) {
                RotationDecision.Propagate((360 - transform.angleCcw * 90) % 360)
            } else {
                RotationDecision.Skip("decoder applied the container rotation to the pixels; unsupported decoder behavior")
            }
        }
    }
}

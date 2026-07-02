package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import eu.darken.sdmse.squeezer.core.CompressibleImage
import java.io.File
import javax.inject.Inject

/**
 * Encodes a [Bitmap] into [outputFile] using a format-specific encoder.
 * Implementations throw on failure so [FileTransaction] aborts the replace.
 *
 * [exifData] is the JPEG-APP1-form EXIF block ("Exif" + two NUL bytes + TIFF header + IFDs) for
 * embed metadata at write time (HEIF); ignored by encoders that handle EXIF separately
 * post-encode (JPEG/WebP via [ExifPreserver]).
 *
 * [rotationDegreesCw] is the display rotation the output container must carry (HEIF `irot`,
 * mirrored from the source so the result renders like the original). Only the HEIF encoder
 * supports it — other formats never receive a non-zero value.
 */
interface ImageEncoder {
    fun encode(
        bitmap: Bitmap,
        mimeType: String,
        quality: Int,
        outputFile: File,
        exifData: ByteArray?,
        rotationDegreesCw: Int = 0,
    )
}

class ImageEncoderFactory @Inject constructor(
    private val bitmapCompressEncoder: BitmapCompressEncoder,
    private val heifWriterEncoder: HeifWriterEncoder,
) {
    fun encoderFor(mimeType: String): ImageEncoder = when (mimeType) {
        in CompressibleImage.HEIC_MIME_TYPES -> heifWriterEncoder
        else -> bitmapCompressEncoder
    }
}

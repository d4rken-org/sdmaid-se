package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import eu.darken.sdmse.squeezer.core.CompressibleImage
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Encodes a [Bitmap] via [Bitmap.compress] for JPEG/WebP outputs. Throws when the encoder
 * returns `false` (previously this was ignored and could leave an empty temp file behind).
 *
 * Ignores [exifData] — EXIF for JPEG/WebP is applied post-encode by [ImageCompressor] via
 * the [ExifPreserver] path.
 */
class BitmapCompressEncoder @Inject constructor() : ImageEncoder {

    override fun encode(
        bitmap: Bitmap,
        mimeType: String,
        quality: Int,
        outputFile: File,
        exifData: ByteArray?,
    ) {
        val format = CompressibleImage.compressFormat(mimeType)
        val ok = outputFile.outputStream().buffered().use { out ->
            bitmap.compress(format, quality, out)
        }
        if (!ok) {
            throw IOException("bitmap.compress($format) returned false for ${outputFile.path}")
        }
    }
}

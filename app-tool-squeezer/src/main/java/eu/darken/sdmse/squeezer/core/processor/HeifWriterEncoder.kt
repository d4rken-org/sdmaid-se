package eu.darken.sdmse.squeezer.core.processor

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.heifwriter.HeifWriter
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Encodes a [Bitmap] to a HEIF/HEIC file via [HeifWriter]. Available from API 28 — callers must
 * have guaranteed this through [eu.darken.sdmse.squeezer.core.CompressibleImage.isHeicEncodingSupported]
 * before reaching this encoder.
 *
 * If [exifData] is non-null, it is embedded into the output via `HeifWriter.addExifData()`.
 * The expected form is the JPEG APP1 payload (`"Exif  "` + TIFF header + IFDs); see
 * [HeifExifBlockBuilder] for how it's produced.
 *
 * A [Bitmap] only carries pixel data, so anything beyond the primary image + EXIF (HDR gain maps,
 * depth maps, ICC profiles, Live/Motion Photo siblings, XMP) is not preserved. Multi-image source
 * files are filtered out at scan time (see `MediaScanner`).
 */
class HeifWriterEncoder @Inject constructor() : ImageEncoder {

    override fun encode(
        bitmap: Bitmap,
        mimeType: String,
        quality: Int,
        outputFile: File,
        exifData: ByteArray?,
        rotationDegreesCw: Int,
    ) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "HeifWriterEncoder requires API 28+, was ${Build.VERSION.SDK_INT}"
        }
        encodeP(bitmap, quality, outputFile, exifData, rotationDegreesCw)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun encodeP(
        bitmap: Bitmap,
        quality: Int,
        outputFile: File,
        exifData: ByteArray?,
        rotationDegreesCw: Int,
    ) {
        if (outputFile.exists()) outputFile.delete()

        val writer = HeifWriter.Builder(
            outputFile.absolutePath,
            bitmap.width,
            bitmap.height,
            HeifWriter.INPUT_MODE_BITMAP,
        )
            .setQuality(quality)
            .setMaxImages(1)
            // Carries the source's display rotation into the output as an irot property.
            .setRotation(rotationDegreesCw)
            .build()

        var closed = false
        try {
            writer.start()
            if (exifData != null) {
                writer.addExifData(0, exifData, 0, exifData.size)
                log(TAG, VERBOSE) { "Embedded ${exifData.size}-byte EXIF block in ${outputFile.path}" }
            }
            writer.addBitmap(bitmap)
            writer.stop(STOP_TIMEOUT_MS)
            writer.close()
            closed = true
        } catch (e: Exception) {
            log(TAG, WARN) { "HeifWriter failed for ${outputFile.path}: ${e.message}" }
            throw IOException("HeifWriter failed for ${outputFile.path}", e)
        } finally {
            if (!closed) {
                try {
                    writer.close()
                } catch (e: Exception) {
                    log(TAG, WARN) { "HeifWriter close after failure threw: ${e.message}" }
                }
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 10_000L
        private val TAG = logTag("Squeezer", "Image", "Encoder", "Heif")
    }
}

package eu.darken.sdmse.compressor.core.processor

import androidx.exifinterface.media.ExifInterface
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import javax.inject.Inject

@Reusable
class ExifPreserver @Inject constructor() {

    data class ExifData(
        val attributes: Map<String, String?>,
    )

    fun extractExif(file: File): ExifData? {
        return try {
            val exif = ExifInterface(file)
            val attributes = PRESERVED_TAGS.associateWith { tag ->
                exif.getAttribute(tag)
            }.filterValues { it != null }

            if (attributes.isEmpty()) {
                log(TAG, VERBOSE) { "No EXIF data found" }
                null
            } else {
                log(TAG, VERBOSE) { "Extracted ${attributes.size} EXIF attributes" }
                ExifData(attributes)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to extract EXIF: ${e.message}" }
            null
        }
    }

    fun applyExif(outputPath: String, exifData: ExifData?) {
        if (exifData == null) return

        try {
            val exif = ExifInterface(outputPath)

            exifData.attributes.forEach { (tag, value) ->
                if (value != null) {
                    exif.setAttribute(tag, value)
                }
            }

            exif.saveAttributes()
            log(TAG, VERBOSE) { "Applied ${exifData.attributes.size} EXIF attributes" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to apply EXIF: ${e.message}" }
        }
    }

    companion object {
        private val TAG = logTag("Compressor", "ExifPreserver")

        private val PRESERVED_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_SOFTWARE,
        )
    }
}

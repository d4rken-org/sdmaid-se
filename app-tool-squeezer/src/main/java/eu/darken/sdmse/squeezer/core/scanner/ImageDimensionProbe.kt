package eu.darken.sdmse.squeezer.core.scanner

import android.graphics.BitmapFactory
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import javax.inject.Inject

/**
 * Reads an image's pixel dimensions from its header without decoding it. Fails open: anything
 * unreadable is `null`, and the scanner treats that as "no downscale marker".
 */
@Reusable
class ImageDimensionProbe @Inject constructor() {

    data class Dimensions(val width: Int, val height: Int)

    fun read(file: File): Dimensions? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth > 0 && options.outHeight > 0) {
            Dimensions(options.outWidth, options.outHeight)
        } else {
            null
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "Dimension probe failed for ${file.path}: ${e.message}" }
        null
    }

    companion object {
        private val TAG = logTag("Squeezer", "Scanner", "Dimensions")
    }
}

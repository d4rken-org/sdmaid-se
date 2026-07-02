package eu.darken.sdmse.common

import android.webkit.MimeTypeMap
import dagger.Reusable
import eu.darken.sdmse.common.files.APathLookup
import javax.inject.Inject

@Reusable
class MimeTypeTool @Inject constructor() {

    suspend fun determineMimeType(lookup: APathLookup<*>): String {
        val ext = lookup.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: EXTENSION_FALLBACKS[ext]
            ?: MimeTypes.Unknown.value
    }

    companion object {
        // MimeTypeMap on some older devices doesn't know HEIC/HEIF; patch only these two.
        private val EXTENSION_FALLBACKS = mapOf(
            "heic" to "image/heic",
            "heif" to "image/heif",
        )
    }
}
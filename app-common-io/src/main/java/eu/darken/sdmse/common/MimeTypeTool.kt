package eu.darken.sdmse.common

import android.webkit.MimeTypeMap
import dagger.Reusable
import eu.darken.sdmse.common.files.APathLookup
import javax.inject.Inject

@Reusable
class MimeTypeTool @Inject constructor() {

    suspend fun determineMimeType(lookup: APathLookup<*>): String = fromExtension(lookup.name.substringAfterLast('.', ""))

    /**
     * The type for a bare extension, e.g. to declare one while creating a document.
     *
     * The fallback equals `ContentResolver.MIME_TYPE_DEFAULT`, which is what the framework itself
     * assumes for an extension it doesn't know, so a name/type pair built from this always matches.
     */
    fun fromExtension(extension: String): String {
        val ext = extension.lowercase()
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
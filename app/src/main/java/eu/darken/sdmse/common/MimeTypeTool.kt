package eu.darken.sdmse.common

import android.webkit.MimeTypeMap
import dagger.Reusable
import eu.darken.sdmse.common.files.APathLookup
import javax.inject.Inject

@Reusable
class MimeTypeTool @Inject constructor() {

    suspend fun determineMimeType(lookup: APathLookup<*>): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(lookup.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: UNKNOWN_TYPE
    }

    companion object {
        private const val UNKNOWN_TYPE = "application/octet-stream"
    }
}
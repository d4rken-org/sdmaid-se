package eu.darken.sdmse.common

import android.webkit.MimeTypeMap
import dagger.Reusable
import eu.darken.sdmse.common.files.APathLookup
import javax.inject.Inject

@Reusable
class MimeTypeTool @Inject constructor() {

    suspend fun determineMimeType(lookup: APathLookup<*>): String {
        val ext = lookup.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: MimeTypes.Unknown.value
    }
}
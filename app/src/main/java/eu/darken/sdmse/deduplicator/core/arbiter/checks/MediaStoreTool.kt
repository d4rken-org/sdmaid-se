package eu.darken.sdmse.deduplicator.core.arbiter.checks

import android.content.ContentResolver
import eu.darken.sdmse.common.files.APath
import javax.inject.Inject


class MediaStoreTool @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    suspend fun has(path: APath): Boolean {
        return true
    }

}
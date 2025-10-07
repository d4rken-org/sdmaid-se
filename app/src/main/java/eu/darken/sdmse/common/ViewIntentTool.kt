package eu.darken.sdmse.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPathLookup
import javax.inject.Inject

@Reusable
class ViewIntentTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mimeTypeTool: MimeTypeTool,
) {

    suspend fun create(lookup: APathLookup<*>): Intent? {
        log(TAG) { "create(): Creating intent for $lookup" }

        if (lookup !is LocalPathLookup) {
            log(TAG) { "create(): Unsupported path type: ${lookup.pathType}" }
            return null
        }

        val javaPath = File(lookup.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", javaPath)
        val mimeType = mimeTypeTool.determineMimeType(lookup)
        log(TAG, VERBOSE) { "create(): MimeType is $mimeType for ${lookup.path}" }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            setDataAndType(uri, mimeType)
        }

        return Intent.createChooser(intent, lookup.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        private val TAG = logTag("ViewIntentTool")
    }
}

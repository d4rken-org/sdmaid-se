package eu.darken.sdmse.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.storage.PathMapper
import javax.inject.Inject

@Reusable
class ViewIntentTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mimeTypeTool: MimeTypeTool,
    private val pathMapper: PathMapper,
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

    suspend fun canOpenFolder(path: APath): Boolean = buildFolderViewIntent(path) != null

    suspend fun createForFolder(path: APath): Intent? {
        log(TAG) { "createForFolder(): Creating intent for $path" }
        val intent = buildFolderViewIntent(path)
        if (intent == null) {
            log(TAG, WARN) { "createForFolder(): Can't view $path externally" }
            return null
        }
        return Intent.createChooser(intent, path.userReadablePath.get(context)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private suspend fun buildFolderViewIntent(path: APath): Intent? {
        if (path !is LocalPath) return null
        val safPath = pathMapper.toSAFPath(path) ?: return null
        if (safPath.segments.isProviderRestricted()) return null

        val treeRoot = safPath.treeRootUri
        val authority = treeRoot.authority ?: return null
        val rootId = treeRoot.lastPathSegment?.substringBefore(':')?.takeIf { it.isNotEmpty() } ?: return null
        val documentId = "$rootId:${safPath.segments.joinToString("/")}"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                DocumentsContract.buildDocumentUri(authority, documentId),
                DocumentsContract.Document.MIME_TYPE_DIR,
            )
        }
        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return intent.takeIf { handlers.isNotEmpty() }
    }

    /**
     * ExternalStorageProvider hides these subtrees on API 30+, so a document URI pointing into
     * them resolves to nothing and the handling app shows an empty/error screen.
     */
    private fun List<String>.isProviderRestricted(): Boolean {
        if (!hasApiLevel(30)) return false
        if (size < 2 || this[0] != "Android") return false
        return this[1] == "data" || this[1] == "obb" || this[1] == "sandbox"
    }

    companion object {
        private val TAG = logTag("ViewIntentTool")
    }
}

package eu.darken.sdmse.common.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.saf.SAFGateway
import eu.darken.sdmse.common.files.core.saf.SAFPath
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Fuck the SAF, this is grating.
 */
@Reusable
class SAFMapper @Inject constructor(
    private val storageManager2: StorageManager2,
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
) {
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun toNavigationUri(localPath: LocalPath): Uri? {
        val osStorage = storageManager2.storageVolumes
            .onEach { log(TAG, VERBOSE) { "Trying to match volume $it against $localPath" } }
            .filter { it.directory != null }
            .firstOrNull { localPath.path.startsWith(it.directory!!.path) }
            .also { log(TAG) { "Target osStorage for $localPath is $it" } }

        if (osStorage?.directory == null) return null

        val prefixFreeFile = localPath.path.replace("${osStorage.directory!!.path}${File.separatorChar}", "")

        return osStorage.documentUri.toString()
            .let { "$it%3A${Uri.encode(prefixFreeFile)}" }
            .let { Uri.parse(it) }
            .also { log(TAG) { "Returning uri for navigation: $it" } }
    }

    suspend fun toSAFPath(localPath: LocalPath): SAFPath? {
        val osStorage = storageManager2.storageVolumes
            .onEach { log(TAG, VERBOSE) { "Trying to match volume $it against $localPath" } }
            .filter { it.directory != null }
            .firstOrNull { localPath.path.startsWith(it.directory!!.path) }
            .also { log(TAG) { "Target osStorage for $localPath is $it" } }
            ?: return null

        val prefixFreeFile = if (osStorage.directory!!.path != localPath.path) {
            localPath.path.replace("${osStorage.directory!!.path}${File.separatorChar}", "")
        } else {
            // Permission is equal to path
            ""
        }

        val segments = if (prefixFreeFile.isEmpty()) {
            emptyList()
        } else {
            prefixFreeFile.split(File.separator)
        }
        return SAFPath.build(
            base = osStorage.treeUri,
            segs = segments.toTypedArray(),
        ).also {
            log(TAG, VERBOSE) { "toSAFPath($localPath):$it" }
        }
    }

//    private fun getSAFPathLegacy(localPath: LocalPath): SAFPath? {
////        val path = localPath.path
////        var volumeRoot: VolumeRoot? = null
////        for (root in roots) {
////            val rootPath = root.storagePath.path
////            if (root.storagePath.path == file.path) {
////                volumeRoot = root
////                break
////            }
////            if (path.startsWith(rootPath) && (volumeRoot == null || rootPath.length > volumeRoot.storagePath.path.length)) {
////                volumeRoot = root
////            }
////        }
////        if (volumeRoot == null) throw IOException("No matching (UriPermission/VolumeRoot): " + file.path)
////        val directRootMatch = file.path == volumeRoot.storagePath.absolutePath
////        val returnUri: Uri
////        val uriBuilder = Uri.Builder()
////        uriBuilder.scheme(ContentResolver.SCHEME_CONTENT)
////        uriBuilder.authority(AUTHORITY)
////        uriBuilder.appendPath(PATH_TREE)
////        uriBuilder.appendPath(volumeRoot.documentId)
////        uriBuilder.appendPath(PATH_DOCUMENT)
////        if (directRootMatch) {
////            uriBuilder.appendPath(volumeRoot.documentId)
////        } else {
////            val subTree = file.path.replace(volumeRoot.storagePath.absolutePath + "/", "")
////            uriBuilder.appendPath(volumeRoot.documentId + subTree)
////        }
////        returnUri = uriBuilder.build()
////        Timber.tag(TAG).v("getUri(): ${file.path} -> $returnUri")
//        val osStorage = storageManager2.storageVolumes
//            .onEach { log(TAG, VERBOSE) { "Trying to match volume $it against $localPath" } }
//            .filter { it.directory != null }
//            .firstOrNull { localPath.directory.startsWith(it.directory!!.path) }
//            .also { log(TAG) { "Target osStorage for $localPath is $it" } }
//            ?: return null
//
//        val prefixFreeFile = localPath.path.replace("${osStorage.pathFile!!.path}${File.separatorChar}", "")
//
//        return SAFPath.build(
//            base = osStorage.treeUri,
//            segs = prefixFreeFile.split(File.separator).toTypedArray()
//        )
//    }

    suspend fun toLocalPath(safPath: SAFPath): LocalPath {
        return TODO()
    }

    fun takePermission(uri: Uri) {
        log(TAG, VERBOSE) { "takePermission(path=$uri)" }

        if (hasPermission(uri)) {
            Timber.tag(TAG).d("Already have permission for %s", uri)
            return
        }

        log(TAG, INFO) { "Taking uri permission for $uri" }

        try {
            contentResolver.takePersistableUriPermission(uri, SAFGateway.RW_FLAGSINT)
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Failed to take permission")
            try {
                contentResolver.releasePersistableUriPermission(uri, SAFGateway.RW_FLAGSINT)
            } catch (e2: SecurityException) {
                Timber.tag(TAG).e(e2, "Error while releasing during error...")
            }
            throw e
        }

        printCurrentPermissions()
    }

    fun releasePermission(path: SAFPath): Boolean {
        log(TAG, INFO) { "Releasing uri permission for $path" }
        contentResolver.releasePersistableUriPermission(path.treeRoot, SAFGateway.RW_FLAGSINT)
        printCurrentPermissions()
        return true
    }

    private fun printCurrentPermissions() {
        val current = getPermissions()
        log(TAG) { "Now holding ${current.size} permissions." }
        for (p in current) {
            log(TAG) { "#${current.indexOf(p)}: $p" }
        }
    }

    fun getPermissions(): Collection<SAFPath> {
        return contentResolver.persistedUriPermissions.map { SAFPath.build(it.uri) }
    }

    fun hasPermission(uri: Uri): Boolean {
        return getPermissions().any { it.pathUri == uri }
    }

    companion object {
        val TAG: String = logTag("SAF", "Mapper")
    }
}
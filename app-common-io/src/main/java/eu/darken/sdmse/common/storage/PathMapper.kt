package eu.darken.sdmse.common.storage

import android.content.ContentResolver
import android.net.Uri
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import java.io.File
import javax.inject.Inject

/**
 * Fuck the SAF, this is grating.
 */
@Reusable
class PathMapper @Inject constructor(
    private val contentResolver: ContentResolver,
    private val storageManager2: StorageManager2,
) {

    suspend fun toSAFPath(localPath: LocalPath): SAFPath? {
        return try {
            val osStorage = storageManager2.storageVolumes
                .onEach { log(TAG, VERBOSE) { "Trying to match volume $it against $localPath" } }
                .filter { it.directory != null }
                .firstOrNull { localPath.path.startsWith(it.directory!!.path) }
                ?.also { log(TAG, VERBOSE) { "Target storageVolumes for $localPath is $it" } }
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

            SAFPath.build(
                base = osStorage.treeUri,
                segs = segments.toTypedArray(),
            ).also {
                log(TAG, VERBOSE) { "toSAFPath() $localPath -> $it" }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to map $localPath: ${e.asLog()}" }
            null
        }
    }

    suspend fun toLocalPath(safPath: SAFPath): LocalPath? {
        return try {
            val osStorage = storageManager2.storageVolumes
                .onEach { log(TAG, VERBOSE) { "Trying to match volume $it against $safPath" } }
                .filter { it.directory != null }
                .firstOrNull { safPath.treeRootUri == it.treeUri }
                ?.also { log(TAG) { "Target storageVolumes for $safPath is $it" } }
                ?: return null

            osStorage.directory?.toLocalPath()?.child(*safPath.segments.toTypedArray())
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to map $safPath:${e.asLog()}" }
            null
        }
    }

    fun takePermission(uri: Uri) {
        log(TAG, VERBOSE) { "takePermission(path=$uri)" }

        if (hasPermission(uri)) {
            log(TAG) { "Already have permission for $uri" }
            return
        }

        log(TAG, INFO) { "Taking uri permission for $uri" }

        try {
            contentResolver.takePersistableUriPermission(uri, SAFGateway.RW_FLAGSINT)
        } catch (e: SecurityException) {
            log(TAG, ERROR) { "Failed to take permission ${e.asLog()}" }
            try {
                contentResolver.releasePersistableUriPermission(uri, SAFGateway.RW_FLAGSINT)
            } catch (e2: SecurityException) {
                log(TAG, ERROR) { "Error while releasing during error... ${e2.asLog()}" }
            }
            throw e
        }

        printCurrentPermissions()
    }

    fun releasePermission(path: SAFPath): Boolean {
        log(TAG, INFO) { "Releasing uri permission for $path" }
        contentResolver.releasePersistableUriPermission(path.treeRootUri, SAFGateway.RW_FLAGSINT)
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
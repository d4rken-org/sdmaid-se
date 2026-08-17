package eu.darken.sdmse.common.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
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
            // StorageVolumeX.directory is a live reflection backed getter, a second read can race an unmount
            val volumes = storageManager2.storageVolumes
                .onEach { log(TAG, VERBOSE) { "Trying to match volume $it against $localPath" } }
                .mapNotNull { volume -> volume.directory?.let { volume to it } }

            // Most specific volume wins. Volumes sharing a directory resolve to the first of them (list order).
            // A volume directory of "/" isn't covered by the containment check, no storage volume mounts there.
            val (osStorage, directory) = volumes
                .filter { (_, dir) ->
                    localPath.path == dir.path || localPath.path.startsWith("${dir.path}${File.separatorChar}")
                }
                .maxByOrNull { (_, dir) -> dir.path.length }
                ?.also { log(TAG, VERBOSE) { "Target storageVolumes for $localPath is ${it.first}" } }
                ?: return null

            val prefixFreeFile = localPath.path
                .substring(directory.path.length)
                .trimStart(File.separatorChar)

            val segments = if (prefixFreeFile.isEmpty()) {
                emptyList()
            } else {
                prefixFreeFile.split(File.separator)
            }

            if (segments.hasTraversal()) {
                log(TAG, WARN) { "Traversal components in $localPath, refusing to map" }
                return null
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

            if (safPath.segments.hasTraversal()) {
                log(TAG, WARN) { "Traversal components in $safPath, refusing to map" }
                return null
            }

            osStorage.directory?.toLocalPath()?.child(*safPath.segments.toTypedArray())
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to map $safPath:${e.asLog()}" }
            null
        }
    }

    /**
     * A single segment can carry embedded separators, java.io.File acts on those, so check the effective components.
     */
    private fun List<String>.hasTraversal(): Boolean = this
        .flatMap { it.split(File.separatorChar) }
        .filter { it.isNotEmpty() }
        .any { it == ".." || it == "." }

    fun takePermission(uri: Uri) {
        log(TAG, VERBOSE) { "takePermission(path=$uri)" }

        if (hasPermission(uri)) {
            log(TAG) { "Already have permission for $uri" }
            return
        }

        log(TAG, INFO) { "Taking uri permission for $uri" }

        // A partial grant (read-only or write-only) can already exist here, we are upgrading it.
        // Cleanup after a failed upgrade must only drop what the failed take could have added.
        val heldFlags = heldFlags(uri)

        try {
            contentResolver.takePersistableUriPermission(uri, SAFGateway.RW_FLAGSINT)
        } catch (e: SecurityException) {
            log(TAG, ERROR) { "Failed to take permission ${e.asLog()}" }
            val releaseFlags = SAFGateway.RW_FLAGSINT and heldFlags.inv()
            if (releaseFlags != 0) {
                try {
                    contentResolver.releasePersistableUriPermission(uri, releaseFlags)
                } catch (e2: SecurityException) {
                    log(TAG, ERROR) { "Error while releasing during error... ${e2.asLog()}" }
                }
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

    /**
     * The app needs read AND write, a partial grant is not good enough and has to be upgraded.
     */
    fun hasPermission(uri: Uri): Boolean = heldFlags(uri) == SAFGateway.RW_FLAGSINT

    private fun heldFlags(uri: Uri): Int = contentResolver.persistedUriPermissions
        .filter { it.uri == uri }
        .fold(0) { flags, permission ->
            var updated = flags
            if (permission.isReadPermission) updated = updated or Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (permission.isWritePermission) updated = updated or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            updated
        }

    companion object {
        val TAG: String = logTag("SAF", "Mapper")
    }
}
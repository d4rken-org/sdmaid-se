package eu.darken.sdmse.common.storage

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager2,
    private val userManager: UserManager2,
) {

    fun getVariable(variableName: String): String? = System.getenv(variableName)

    val ourCodeCacheDirs: Collection<LocalPath>
        get() = listOf(context.codeCacheDir.toLocalPath())

    val ourCacheDirs: Collection<LocalPath>
        get() = listOf(context.cacheDir.toLocalPath())

    val ourExternalCacheDirs: Collection<LocalPath>
        get() = context.externalCacheDirs
            .filterNotNull()  // Can be non-empty and contains a NULL values
            .map { it.toLocalPath() }

    val downloadCacheDirs: Collection<LocalPath>
        get() = setOf(
            Environment.getDownloadCacheDirectory().toLocalPath(),
            dataDir.child("cache"),
            LocalPath.build("/cache"),
        )
            .sortedBy { it.path == Environment.getDownloadCacheDirectory().path }

    val systemDir: LocalPath
        get() = Environment.getRootDirectory().toLocalPath()

    val dataDir: LocalPath
        get() = Environment.getDataDirectory().toLocalPath()

    val externalDirs: List<LocalPath>
        get() = ContextCompat.getExternalFilesDirs(context, null)
            .filter { it != null && it.isAbsolute }
            .mapNotNull { base ->
                var root = base
                for (i in 0..3) {
                    root = root.parentFile
                    if (root == null) break
                }
                root?.let { LocalPath.build(it) }
            }

    val publicDataDirs: List<LocalPath>
        get() = ContextCompat.getExternalFilesDirs(context, null)
            .filter { it != null && it.isAbsolute }
            .mapNotNull { base ->
                var root = base
                for (i in 0..1) {
                    root = root.parentFile
                    if (root == null) break
                }
                root?.let { LocalPath.build(it) }
            }

    suspend fun getPublicPrimaryStorage(userHandle: UserHandle2): LocalPath? {
        val userPath = Environment.getExternalStorageDirectory().let { path ->
            if (path.name.toIntOrNull() == userManager.currentUser().handle.handleId) {
                // If this matches, then we can make assumptions on the path
                File(path.parentFile!!, userHandle.handleId.toString())
            } else {
                path
            }
        }

        val volume = storageManager.getStorageVolume(userPath)
        if (volume == null) {
            log(TAG, WARN) { "Can't find volume for $userPath" }
            return null
        }

        return LocalPath.build(userPath)
    }

    // http://androidxref.com/5.1.1_r6/xref/frameworks/base/core/java/android/os/Environment.java#136
    suspend fun getPublicSecondaryStorage(userHandle: UserHandle2): Set<LocalPath> {
        val primary = getPublicPrimaryStorage(userHandle)
        if (primary == null) {
            log(TAG, WARN) { "No primary storage? No secondary storage!" }
            return emptySet()
        }

        val pathResult = mutableListOf<LocalPath>()

        ContextCompat.getExternalFilesDirs(context, null)
            .filterNotNull()
            .filter { it.isAbsolute }
            .mapNotNull { extMyDir ->
                var findRoot: File? = extMyDir
                for (i in 0..3) { // Android/data/pkg
                    findRoot = findRoot?.parentFile
                    if (findRoot == null) break
                }
                findRoot
            }
            .map { root ->
                if (root.name.toIntOrNull() == userManager.currentUser().handle.handleId) {
                    // If this matches, then we can make assumptions on the path
                    File(root.parentFile!!, userHandle.handleId.toString())
                } else {
                    root
                }
            }
            .map { LocalPath.build(it) }
            .forEach {
                log(TAG) { "Secondary public storage: $it" }
                pathResult.add(it)
            }

        return pathResult.filter { it != primary }.toSet()
    }

    suspend fun getPublicStorage(userHandle: UserHandle2): Collection<LocalPath> {
        val paths = mutableListOf<LocalPath>()
        getPublicPrimaryStorage(userHandle)?.let { paths.add(it) }
        paths.addAll(getPublicSecondaryStorage(userHandle))
        return paths
    }

    companion object {
        val TAG = logTag("DataArea", "DeviceEnvironment")
    }
}
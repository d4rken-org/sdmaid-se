package eu.darken.sdmse.common.storage

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.toLocalPath
import eu.darken.sdmse.common.user.UserHandle2
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager2,
) {

    fun getVariable(variableName: String): String? = System.getenv(variableName)

    val cacheDir: LocalPath
        get() = Environment.getDownloadCacheDirectory().toLocalPath()

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

    fun getPublicPrimaryStorage(userHandle: UserHandle2): LocalPath {
        val path = Environment.getExternalStorageDirectory()
        val volume = storageManager.getStorageVolume(path)
        requireNotNull(volume) { "Can't find volume for $path" }
        return LocalPath.build(path)
    }

    // http://androidxref.com/5.1.1_r6/xref/frameworks/base/core/java/android/os/Environment.java#136
    fun getPublicSecondaryStorage(userHandle: UserHandle2): Collection<LocalPath> {
        val pathResult = mutableListOf<LocalPath>()
        for (extMyDir in ContextCompat.getExternalFilesDirs(context, null)) {
            if (extMyDir == null) continue
            if (!extMyDir.isAbsolute) continue
            var findRoot = extMyDir
            for (i in 0..3) {
                findRoot = findRoot.parentFile
                if (findRoot == null) break
            }
            if (findRoot == null) continue
            pathResult.add(LocalPath.build(findRoot))
        }
        val primary = getPublicPrimaryStorage(userHandle)
        return pathResult.filter { it != primary }
    }

    fun getPublicStorage(userHandle: UserHandle2): Collection<LocalPath> {
        return listOf(getPublicPrimaryStorage(userHandle)).plus(getPublicSecondaryStorage(userHandle))
    }

    companion object {
        val TAG = logTag("DataArea", "DeviceEnvironment")
    }
}
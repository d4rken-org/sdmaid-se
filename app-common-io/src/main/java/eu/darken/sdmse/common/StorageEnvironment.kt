package eu.darken.sdmse.common

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.asFile
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.toLocalPath
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.wrps.storagemanager.StorageManager2
import eu.darken.sdmse.common.wrps.storagemanager.StorageVolumeX
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManagerX: StorageManager2
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

    fun getPublicPrimaryStorage(userHandle: UserHandle2): DeviceStorage {
        val path = Environment.getExternalStorageDirectory()
        val volume = storageManagerX.getStorageVolume(path)
        requireNotNull(volume) { "Can't find volume for $path" }
        return DeviceStorage(
            LocalPath.build(path),
            SAFPath.build(buildUri(volume))
        )
    }

    // http://androidxref.com/5.1.1_r6/xref/frameworks/base/core/java/android/os/Environment.java#136
    fun getPublicSecondaryStorage(userHandle: UserHandle2): Collection<DeviceStorage> {
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
        val primary = getPublicPrimaryStorage(userHandle).localPath
        return pathResult
            .filter { it != primary }
            .map {
                val volume = storageManagerX.getStorageVolume(it.asFile())
                requireNotNull(volume) { "Can't find volume for $it" }
                DeviceStorage(
                    it,
                    SAFPath.build(buildUri(volume))
                )
            }
    }


    fun getPublicStorage(userHandle: UserHandle2): Collection<DeviceStorage> {
        return listOf(getPublicPrimaryStorage(userHandle)).plus(getPublicSecondaryStorage(userHandle))
    }

    fun mapToSAF(localPath: LocalPath): SAFPath {
        TODO()
    }

    fun mapToLocal(safPath: SAFPath): LocalPath {
        TODO()
    }

    data class DeviceStorage(
        val localPath: LocalPath,
        val safPath: SAFPath
    )

    companion object {

        val TAG = logTag("DataArea", "DeviceEnvironment")

        internal fun buildUri(volume: StorageVolumeX): Uri {
            return Uri.parse("content://com.android.externalstorage.documents/tree/${Uri.encode(volume.uuid)}")
        }

    }
}
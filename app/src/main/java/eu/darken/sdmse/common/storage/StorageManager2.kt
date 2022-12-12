package eu.darken.sdmse.common.storage

import android.annotation.SuppressLint
import android.content.Context
import android.os.storage.StorageManager
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import java.io.File
import java.lang.reflect.Method
import java.util.*
import javax.inject.Inject

@Reusable
class StorageManager2 @Inject constructor(@ApplicationContext context: Context) {
    private val storageManager: StorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private var _getVolumes: Method? = null

    val storageVolumes: List<StorageVolumeX>
        get() = storageManager.storageVolumes.map { StorageVolumeX(it) }

    val volumes: List<VolumeInfoX>?
        get() = try {
            if (_getVolumes == null) _getVolumes = storageManager.javaClass.getMethod("getVolumes")

            val storageInfos = _getVolumes!!.invoke(storageManager) as List<*>
            storageInfos.filterNotNull().map { VolumeInfoX(it) }
        } catch (e: ReflectiveOperationException) {
            log(TAG, ERROR) { "StorageManagerX.volumes reflection issue: $e" }
            null
        }

    fun getStorageVolume(file: File): StorageVolumeX? {
        var volume: StorageVolumeX? = null
        if (hasApiLevel(24)) {
            @SuppressLint("NewApi")
            volume = storageManager.getStorageVolume(file)?.let { StorageVolumeX(it) }
        }
        if (volume == null) {
            volume = storageVolumes.singleOrNull { it.path == file.path }
        }
        return volume
    }

    companion object {
        val TAG: String = logTag("StorageManager2")
    }
}
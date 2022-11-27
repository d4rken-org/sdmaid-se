package eu.darken.sdmse.common.files.core.saf.oswrapper.manager

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import timber.log.Timber
import java.io.File
import java.lang.reflect.Method
import java.util.*
import javax.inject.Inject

@Reusable
class StorageManagerX @Inject constructor(@ApplicationContext context: Context) {
    private val storageManager: StorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private var _getVolumeList: Method? = null
    private var _getVolumes: Method? = null

    @get:RequiresApi(Build.VERSION_CODES.KITKAT)
    @get:SuppressLint("NewApi")
    val storageVolumes: List<StorageVolumeX>
        get() {
            val svList: MutableList<StorageVolumeX> = ArrayList()

            if (hasApiLevel(24)) {
                for (vol in storageManager.storageVolumes) svList.add(StorageVolumeX(vol))
            } else {
                try {
                    if (_getVolumeList == null) _getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
                    val storageVolumes = _getVolumeList!!.invoke(storageManager) as Array<Any>
                    for (storageVolume in storageVolumes) svList.add(StorageVolumeX(storageVolume))
                } catch (e: ReflectiveOperationException) {
                    Timber.tag(TAG).e(e, "StorageManagerX.volumeList reflection issue")

                }
            }

            return svList
        }


    @get:RequiresApi(Build.VERSION_CODES.M)
    val volumes: List<VolumeInfoX>?
        get() = try {
            if (_getVolumes == null) _getVolumes = storageManager.javaClass.getMethod("getVolumes")

            val storageInfos = _getVolumes!!.invoke(storageManager) as List<*>
            storageInfos.filterNotNull().map { VolumeInfoX(it) }
        } catch (e: ReflectiveOperationException) {
            Timber.tag(TAG).e(e, "StorageManagerX.volumes reflection issue")
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
        val TAG: String = logTag("StorageManagerX")
        private const val DEFAULT_THRESHOLD_PERCENTAGE = 10
        private const val DEFAULT_THRESHOLD_MAX_BYTES = (500 * 1024 * 1024).toLong()
        private const val DEFAULT_FULL_THRESHOLD_BYTES = (1024 * 1024).toLong()
    }
}
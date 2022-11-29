package eu.darken.sdmse.common.wrps.storagemanager

import android.annotation.TargetApi
import android.os.Build
import timber.log.Timber
import java.lang.reflect.Method


/**
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/storage/DiskInfo.java
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
class DiskInfoX(private val diskInfoObject: Any) {
    private val volumeInfoClass: Class<*> = diskInfoObject.javaClass

    private val methodGetId: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getId")
        } catch (e: Exception) {
            Timber.w(e, "volumeInfoClass.getMethod(\"getId\")")
            null
        }
    }
    val id: String?
        get() = try {
            methodGetId?.invoke(diskInfoObject) as? String
        } catch (e: ReflectiveOperationException) {
            Timber.w("DiskInfoX.id reflection failed")
            null
        }

    private val methodGetDescription: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getDescription")
        } catch (e: Exception) {
            Timber.w(e, "volumeInfoClass.getMethod(\"getDescription\")")
            null
        }
    }
    val description: String?
        get() = try {
            methodGetDescription?.invoke(diskInfoObject) as? String?
        } catch (e: ReflectiveOperationException) {
            Timber.w("DiskInfoX.description reflection failed")
            null
        }

    private val methodIsAdoptable: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isAdoptable")
        } catch (e: Exception) {
            Timber.w(e, "volumeInfoClass.getMethod(\"isAdoptable\")")
            null
        }
    }
    val isAdoptable: Boolean?
        get() = try {
            methodIsAdoptable?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            Timber.w("DiskInfoX.isAdoptable reflection failed")
            null
        }

    private val methodIsDefaultPrimary: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isDefaultPrimary")
        } catch (e: Exception) {
            Timber.w(e, "volumeInfoClass.getMethod(\"isDefaultPrimary\")")
            null
        }
    }
    val isDefaultPrimary: Boolean?
        get() = try {
            methodIsDefaultPrimary?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            Timber.w("DiskInfoX.isDefaultPrimary reflection failed")
            null
        }

    private val methodIsSd: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isSd")
        } catch (e: Exception) {
            Timber.w(e, "volumeInfoClass.getMethod(\"isSd\")")
            null
        }
    }
    val isSd: Boolean?
        get() = try {
            methodIsSd?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            Timber.w("DiskInfoX.isSd reflection failed")
            null
        }

    private val methodIsUsb: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isUsb")
        } catch (e: Exception) {
            Timber.w(e, "volumeInfoClass.getMethod(\"isUsb\")")
            null
        }
    }
    val isUsb: Boolean?
        get() = try {
            methodIsUsb?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            Timber.w("DiskInfoX.isUsb reflection failed")
            null
        }

    override fun toString(): String = "DiskInfoX($diskInfoObject)"

}

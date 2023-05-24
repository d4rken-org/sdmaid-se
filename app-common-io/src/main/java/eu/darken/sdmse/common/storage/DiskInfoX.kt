package eu.darken.sdmse.common.storage

import android.annotation.TargetApi
import android.os.Build
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
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
            log(WARN) { "volumeInfoClass.getMethod(\"getId\"): ${e.asLog()}" }
            null
        }
    }
    val id: String?
        get() = try {
            methodGetId?.invoke(diskInfoObject) as? String
        } catch (e: ReflectiveOperationException) {
            log(WARN) { "DiskInfoX.id reflection failed" }
            null
        }

    private val methodGetDescription: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getDescription")
        } catch (e: Exception) {
            log(WARN) { "volumeInfoClass.getMethod(\"getDescription\"): ${e.asLog()}" }
            null
        }
    }
    val description: String?
        get() = try {
            methodGetDescription?.invoke(diskInfoObject) as? String?
        } catch (e: ReflectiveOperationException) {
            log(WARN) { "DiskInfoX.description reflection failed" }
            null
        }

    private val methodIsAdoptable: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isAdoptable")
        } catch (e: Exception) {
            log(WARN) { "volumeInfoClass.getMethod(\"isAdoptable\"): ${e.asLog()}" }
            null
        }
    }
    val isAdoptable: Boolean?
        get() = try {
            methodIsAdoptable?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            log(WARN) { "DiskInfoX.isAdoptable reflection failed" }
            null
        }

    private val methodIsDefaultPrimary: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isDefaultPrimary")
        } catch (e: Exception) {
            log(WARN) { "volumeInfoClass.getMethod(\"isDefaultPrimary\"): ${e.asLog()}" }
            null
        }
    }
    val isDefaultPrimary: Boolean?
        get() = try {
            methodIsDefaultPrimary?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            log(WARN) { "DiskInfoX.isDefaultPrimary reflection failed" }
            null
        }

    private val methodIsSd: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isSd")
        } catch (e: Exception) {
            log(WARN) { "volumeInfoClass.getMethod(\"isSd\"): ${e.asLog()}" }
            null
        }
    }
    val isSd: Boolean?
        get() = try {
            methodIsSd?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            log(WARN) { "DiskInfoX.isSd reflection failed" }
            null
        }

    private val methodIsUsb: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isUsb")
        } catch (e: Exception) {
            log(WARN) { "volumeInfoClass.getMethod(\"isUsb\"): ${e.asLog()}" }
            null
        }
    }
    val isUsb: Boolean?
        get() = try {
            methodIsUsb?.invoke(diskInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            log(WARN) { "DiskInfoX.isUsb reflection failed" }
            null
        }

    override fun toString(): String = "DiskInfoX($diskInfoObject)"

}

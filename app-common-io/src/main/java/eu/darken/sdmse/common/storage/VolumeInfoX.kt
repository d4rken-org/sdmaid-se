package eu.darken.sdmse.common.storage

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import java.lang.reflect.Method

/**
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/storage/VolumeInfo.java
 */
@TargetApi(Build.VERSION_CODES.M)
class VolumeInfoX internal constructor(private val mVolumeInfoObject: Any) {
    private val volumeInfoClass: Class<*> = mVolumeInfoObject.javaClass

    private val methodGetDisk: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getDisk")
        } catch (e: Exception) {
            log(TAG, WARN) { "volumeInfoClass.getMethod(\"getDisk\"): ${e.asLog()}" }
            null
        }
    }
    val disk: DiskInfoX?
        get() = try {
            methodGetDisk?.invoke(mVolumeInfoObject)?.let { DiskInfoX(it) }
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.disk reflection failed" }
            null
        }

    val isMounted: Boolean
        get() = state == STATE_MOUNTED

    val isPrivate: Boolean
        get() = type == TYPE_PRIVATE

    val isEmulated: Boolean
        get() = type == TYPE_EMULATED

    val isRemovable: Boolean
        get() = when (val type = type) {
            TYPE_EMULATED -> id != ID_EMULATED_INTERNAL
            else -> type == TYPE_PUBLIC
        }

    private val methodGetType: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getType")
        } catch (e: Exception) {
            log(TAG, WARN) { "volumeInfoClass.getMethod(\"getType\"): ${e.asLog()}" }
            null
        }
    }
    val type: Int?
        get() = try {
            methodGetType?.invoke(mVolumeInfoObject) as? Int
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.type reflection failed" }
            null
        }

    private val methodGetState: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getState")
        } catch (e: Exception) {
            log(TAG, WARN) { "volumeInfoClass.getMethod(\"getState\"): ${e.asLog()}" }
            null
        }
    }
    val state: Int?
        get() = try {
            methodGetState?.invoke(mVolumeInfoObject) as? Int
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.state reflection failed" }
            null
        }

    private val methodGetId: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getId")
        } catch (e: Exception) {
            log(TAG, WARN) { "volumeInfoClass.getMethod(\"getId\"): ${e.asLog()}" }
            null
        }
    }
    val id: String?
        get() = try {
            methodGetId?.invoke(mVolumeInfoObject) as? String
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.id reflection failed" }
            null
        }

    private val methodIsPrimary: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isPrimary")
        } catch (e: Exception) {
            log(TAG, WARN) { "Reflection failed: volumeInfoClass.getMethod(\"isPrimary\"): ${e.asLog()}" }
            null
        }
    }
    val isPrimary: Boolean?
        get() = try {
            methodIsPrimary?.invoke(mVolumeInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.isPrimary reflection failed" }
            null
        }

    private val methodGetFsUuid: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getFsUuid")
        } catch (e: Exception) {
            log(TAG, WARN) { "Reflection failed: volumeInfoClass.getMethod(\"getId\"): ${e.asLog()}" }
            null
        }
    }
    val fsUuid: String?
        get() = try {
            methodGetFsUuid?.invoke(mVolumeInfoObject) as? String?
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.fsUuid reflection failed" }
            null
        }

    private val methodGetPath: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getPath")
        } catch (e: Exception) {
            log(TAG, WARN) { "Reflection failed: volumeInfoClass.getMethod(\"getPath\"): ${e.asLog()}" }
            null
        }
    }
    val path: File?
        get() = try {
            methodGetPath?.invoke(mVolumeInfoObject) as? File?
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.path reflection failed" }
            null
        }

    private val methodGetDescription: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getDescription")
        } catch (e: Exception) {
            // https://github.com/d4rken/sdmaid-public/issues/1678
            log(TAG, WARN) { "Reflection failed: volumeInfoClass.getMethod(\"getDescription\"): ${e.asLog()}" }
            null
        }
    }
    val description: String?
        get() = try {
            methodGetDescription?.invoke(mVolumeInfoObject) as? String?
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.description reflection failed" }
            null
        }

    private val methodGetPathForUser: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getPathForUser", Int::class.javaPrimitiveType)
        } catch (e: Exception) {
            // https://github.com/d4rken/sdmaid-public/issues/1678
            log(TAG, WARN) {
                "Reflection failed:  volumeInfoClass.getMethod(\"getPathForUser\", Int::class.javaPrimitiveType): ${e.asLog()}"
            }
            null
        }
    }

    fun getPathForUser(userId: Int): File? = try {
        methodGetPathForUser?.invoke(mVolumeInfoObject, userId) as? File?
    } catch (e: ReflectiveOperationException) {
        log(TAG, WARN) { "VolumeInfo.getPathForUser($userId) reflection failed" }
        null
    }

    override fun toString(): String =
        "VolumeInfoX(fsUuid=$fsUuid,state=$state,path=$path,description=$description,disk=$disk)"

    companion object {
        private val TAG = logTag("VolumeInfoX")
        private const val TYPE_PUBLIC = 0
        private const val TYPE_PRIVATE = 1
        private const val TYPE_EMULATED = 2
        private const val TYPE_ASEC = 3
        private const val TYPE_OBB = 4
        const val STATE_UNMOUNTED = 0
        const val STATE_CHECKING = 1
        const val STATE_MOUNTED = 2
        const val STATE_MOUNTED_READ_ONLY = 3
        const val STATE_FORMATTING = 4
        const val STATE_EJECTING = 5
        const val STATE_UNMOUNTABLE = 6
        const val STATE_REMOVED = 7
        const val STATE_BAD_REMOVAL = 8

        /**
         * Real volume representing internal emulated storage
         */
        private const val ID_EMULATED_INTERNAL = "emulated"

        @SuppressLint("PrivateApi")
        fun getEnvironmentForState(state: Int): String? = try {
            val volumeInfoClass = Class.forName("android.os.storage.VolumeInfo")
            val methodGetEnvironmentForState =
                volumeInfoClass.getMethod("getEnvironmentForState", Int::class.javaPrimitiveType)
            methodGetEnvironmentForState.invoke(null, state) as String?
        } catch (e: ReflectiveOperationException) {
            log(TAG, WARN) { "VolumeInfo.getEnvironmentForState reflection failed" }
            null
        }
    }
}
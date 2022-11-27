package eu.darken.sdmse.common.files.core.saf.oswrapper.manager

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import eu.darken.sdmse.common.debug.logging.logTag
import timber.log.Timber
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
            Timber.tag(TAG).w(e, "volumeInfoClass.getMethod(\"getDisk\")")
            null
        }
    }
    val disk: DiskInfoX?
        get() = try {
            methodGetDisk?.invoke(mVolumeInfoObject)?.let { DiskInfoX(it) }
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.disk reflection failed")
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
            Timber.tag(TAG).w(e, "volumeInfoClass.getMethod(\"getType\")")
            null
        }
    }
    val type: Int?
        get() = try {
            methodGetType?.invoke(mVolumeInfoObject) as? Int
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.type reflection failed")
            null
        }

    private val methodGetState: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getState")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "volumeInfoClass.getMethod(\"getState\")")
            null
        }
    }
    val state: Int?
        get() = try {
            methodGetState?.invoke(mVolumeInfoObject) as? Int
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.state reflection failed")
            null
        }

    private val methodGetId: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getId")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "volumeInfoClass.getMethod(\"getId\")")
            null
        }
    }
    val id: String?
        get() = try {
            methodGetId?.invoke(mVolumeInfoObject) as? String
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.id reflection failed")
            null
        }

    private val methodIsPrimary: Method? by lazy {
        try {
            volumeInfoClass.getMethod("isPrimary")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Reflection failed: volumeInfoClass.getMethod(\"isPrimary\")")
            null
        }
    }
    val isPrimary: Boolean?
        get() = try {
            methodIsPrimary?.invoke(mVolumeInfoObject) as? Boolean
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.isPrimary reflection failed")
            null
        }

    private val methodGetFsUuid: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getFsUuid")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Reflection failed: volumeInfoClass.getMethod(\"getId\")")
            null
        }
    }
    val fsUuid: String?
        get() = try {
            methodGetFsUuid?.invoke(mVolumeInfoObject) as? String?
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.fsUuid reflection failed")
            null
        }

    private val methodGetPath: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getPath")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Reflection failed: volumeInfoClass.getMethod(\"getPath\")")
            null
        }
    }
    val path: File?
        get() = try {
            methodGetPath?.invoke(mVolumeInfoObject) as? File?
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.path reflection failed")
            null
        }

    private val methodGetDescription: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getDescription")
        } catch (e: Exception) {
            // https://github.com/d4rken/sdmaid-public/issues/1678
            Timber.tag(TAG).w(e, "Reflection failed: volumeInfoClass.getMethod(\"getDescription\")")
            null
        }
    }
    val description: String?
        get() = try {
            methodGetDescription?.invoke(mVolumeInfoObject) as? String?
        } catch (e: ReflectiveOperationException) {
            Timber.w("VolumeInfo.description reflection failed")
            null
        }

    private val methodGetPathForUser: Method? by lazy {
        try {
            volumeInfoClass.getMethod("getPathForUser", Int::class.javaPrimitiveType)
        } catch (e: Exception) {
            // https://github.com/d4rken/sdmaid-public/issues/1678
            Timber.tag(TAG)
                .w(e, "Reflection failed:  volumeInfoClass.getMethod(\"getPathForUser\", Int::class.javaPrimitiveType)")
            null
        }
    }

    fun getPathForUser(userId: Int): File? = try {
        methodGetPathForUser?.invoke(mVolumeInfoObject, userId) as? File?
    } catch (e: ReflectiveOperationException) {
        Timber.w("VolumeInfo.getPathForUser($userId) reflection failed")
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
            Timber.w("VolumeInfo.getEnvironmentForState reflection failed")
            null
        }
    }
}
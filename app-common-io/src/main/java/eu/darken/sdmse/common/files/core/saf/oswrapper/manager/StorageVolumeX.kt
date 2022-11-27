package eu.darken.sdmse.common.files.core.saf.oswrapper.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.UserHandle
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import timber.log.Timber
import java.io.File
import java.lang.reflect.Method

/**
 * http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.2_r1/android/os/storage/StorageVolume.java
 */
@Parcelize
@RequiresApi(Build.VERSION_CODES.KITKAT)
@TypeParceler<Any, AnyParceler>
class StorageVolumeX constructor(
    private val volumeObj: Any
) : Parcelable {
    private val volumeClass: Class<*> = volumeObj.javaClass

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val volume: StorageVolume
        get() = volumeObj as StorageVolume

    private val methodIsPrimary: Method? by lazy {
        try {
            volumeClass.getMethod("isPrimary")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"isPrimary\")")
            null
        }
    }

    @get:SuppressLint("NewApi")
    val isPrimary: Boolean?
        get() = if (hasApiLevel(24)) {
            volume.isPrimary
        } else {
            try {
                methodIsPrimary?.invoke(volumeObj) as? Boolean
            } catch (e: ReflectiveOperationException) {
                Timber.tag(TAG).d("StorageVolume.isPrimary reflection failed.")
                null
            }
        }

    private val methodIsRemovable: Method? by lazy {
        try {
            volumeClass.getMethod("isRemovable")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"isRemovable\")")
            null
        }
    }

    @get:SuppressLint("NewApi")
    val isRemovable: Boolean?
        get() = if (hasApiLevel(24)) {
            volume.isRemovable
        } else {
            try {
                methodIsRemovable?.invoke(volumeObj) as? Boolean
            } catch (e: ReflectiveOperationException) {
                Timber.tag(TAG).d("StorageVolume.isRemovable reflection failed.")
                null
            }
        }

    private val methodIsEmulated: Method? by lazy {
        try {
            volumeClass.getMethod("isEmulated")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"isEmulated\")")
            null
        }
    }

    /**
     * Returns true if the volume is emulated.
     *
     * @return is removable
     */
    @get:SuppressLint("NewApi")
    val isEmulated: Boolean?
        get() = if (hasApiLevel(24)) {
            volume.isEmulated
        } else {
            try {
                methodIsEmulated?.invoke(volumeObj) as? Boolean
            } catch (e: ReflectiveOperationException) {
                Timber.tag(TAG).d("StorageVolume.isEmulated reflection failed.")
                null
            }
        }

    private val methodGetUuid: Method? by lazy {
        try {
            volumeClass.getMethod("getUuid")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"getUuid\")")
            null
        }
    }

    /**
     * http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.0_r1/com/android/server/MountService.java#904
     */
    @get:SuppressLint("NewApi")
    val uuid: String?
        get() = if (hasApiLevel(24)) {
            volume.uuid
        } else {
            try {
                methodGetUuid?.invoke(volumeObj) as? String?
            } catch (e: ReflectiveOperationException) {
                Timber.tag(TAG).d("StorageVolume.uuid reflection failed.")
                null
            }
        }

    private val methodGetState: Method? by lazy {
        try {
            volumeClass.getMethod("getState")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"getState\")")
            null
        }
    }

    @get:SuppressLint("NewApi")
    val state: String?
        get() = try {
            if (hasApiLevel(24)) {
                volume.state
            } else {
                methodGetState?.invoke(volumeObj) as? String
            }
        } catch (e: ReflectiveOperationException) {
            Timber.tag(TAG).d("StorageVolume.state reflection failed.")
            null
        }

    private val methodGetPath: Method? by lazy {
        try {
            volumeClass.getMethod("getPath")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"getPath\")")
            null
        }
    }

    val path: String?
        get() = try {
            methodGetPath?.invoke(volumeObj) as? String
        } catch (e: ReflectiveOperationException) {
            Timber.tag(TAG).d("StorageVolume.path reflection failed.")
            null
        }

    private val methodGetPathFile: Method? by lazy {
        try {
            volumeClass.getMethod("getPathFile")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"getPathFile\")")
            null
        }
    }

    val pathFile: File?
        get() = try {
            methodGetPathFile?.invoke(volumeObj) as? File
        } catch (e: ReflectiveOperationException) {
            Timber.tag(TAG).d("StorageVolume.pathFile reflection failed.")
            null
        }

    private val methodGetUserLabel: Method? by lazy {
        try {
            volumeClass.getMethod("getUserLabel")
        } catch (e: Exception) {
            Timber.tag(TAG).d("volumeClass.getMethod(\"getUserLabel\")")
            null
        }
    }

    val userLabel: String?
        get() = try {
            methodGetUserLabel?.invoke(volumeObj) as? String
        } catch (e: ReflectiveOperationException) {
            Timber.tag(TAG).d("StorageVolume.userLabel reflection failed.")
            null
        }

    private val methodGetDescription: Method? by lazy {
        try {
            volumeClass.getMethod("getDescription", Context::class.java)
        } catch (e: Exception) {
            Timber.tag(TAG).d(" volumeClass.getMethod(\"getDescription\", Context::class.java)")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getDescription(context: Context?): String? = if (hasApiLevel(30)) {
        volume.getDescription(context)
    } else {
        try {
            try {
                methodGetDescription?.invoke(volumeObj, context) as? String
            } catch (e: Resources.NotFoundException) {
                Timber.tag(TAG).e(e)
                null
            }
        } catch (e: ReflectiveOperationException) {
            Timber.tag(TAG).d("StorageVolume.getDescription reflection failed.")
            null
        }
    }

    private val methodGetOwner: Method? by lazy {
        try {
            volumeClass.getMethod("getOwner")
        } catch (e: Exception) {
            Timber.tag(TAG).d(" volumeClass.getMethod(\"getDescription\", Context::class.java)")
            null
        }
    }

    val owner: UserHandle?
        get() = try {
            methodGetOwner?.invoke(volumeObj) as? UserHandle
        } catch (e: NoSuchMethodException) {
            if (!hasApiLevel(30)) Timber.tag(TAG).e("StorageVolumeX.getOwner() unavailable.")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "StorageVolumeX.getOwner() threw an error.")
            null
        }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun createAccessIntent(directory: String?): Intent? {
        return volume.createAccessIntent(directory)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun createOpenDocumentTreeIntent(): Intent {
        return volume.createOpenDocumentTreeIntent()
    }

    @get:RequiresApi(Build.VERSION_CODES.Q)
    val rootUri: Uri
        get() = createOpenDocumentTreeIntent()
            .getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI)!!

    @get:RequiresApi(Build.VERSION_CODES.Q)
    val documentUri: Uri
        get() = rootUri.toString()
            .replace("/root/", "/document/")
            .let { Uri.parse(it) }

    @get:RequiresApi(Build.VERSION_CODES.Q)
    val treeUri: Uri
        get() = rootUri.toString()
            .replace("/root/", "/tree/")
            .let { Uri.parse(it) }

    @get:RequiresApi(Build.VERSION_CODES.R)
    val directory: File?
        get() = if (hasApiLevel(30)) {
            volume.directory
        } else {
            null
        }

    fun dump(): String = try {
        val dumpMethod = volumeClass.getMethod("dump")
        dumpMethod.invoke(volumeObj) as String
    } catch (e: Exception) {
        Timber.tag(TAG).v("dump() unavailable.")
        this.toString()
    }

    override fun toString(): String {
        val sb = StringBuilder("StorageVolumeX(")
        sb.append("uuid=$uuid, ")
        sb.append("state=$state, ")
        sb.append("path=$path, ")
        sb.append("primary=$isPrimary, ")
        sb.append("emulated=$isEmulated, ")
        sb.append("owner=$owner, ")
        sb.append("userlabel=$userLabel, ")
        if (hasApiLevel(29)) {
            sb.append("rootUri=$rootUri")
        }
        sb.append(")")
        return sb.toString()
    }

    companion object {
        val TAG: String = logTag("StorageVolumeX")
    }
}

internal object AnyParceler : Parceler<Any> {
    override fun create(parcel: Parcel): Any = parcel.readParcelable(StorageVolumeX::class.java.classLoader)!!

    override fun Any.write(parcel: Parcel, flags: Int) = parcel.writeParcelable(this as Parcelable, flags)
}
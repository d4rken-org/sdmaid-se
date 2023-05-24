package eu.darken.sdmse.common.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.os.UserHandle
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.io.File
import java.lang.reflect.Method

/**
 * http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.2_r1/android/os/storage/StorageVolume.java
 */
@Parcelize
@TypeParceler<Any, AnyParceler>
class StorageVolumeX constructor(
    private val volumeObj: Any
) : Parcelable {
    private val volumeClass: Class<*> = volumeObj.javaClass


    private val volume: StorageVolume
        get() = volumeObj as StorageVolume

    val isPrimary: Boolean
        get() = volume.isPrimary

    @get:SuppressLint("NewApi")
    val isRemovable: Boolean
        get() = volume.isRemovable

    /**
     * Returns true if the volume is emulated.
     *
     * @return is removable
     */
    val isEmulated: Boolean
        get() = volume.isEmulated

    /**
     * http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.0_r1/com/android/server/MountService.java#904
     */
    val uuid: String?
        get() = volume.uuid

    val state: String?
        get() = volume.state

    val isMounted: Boolean
        get() = volume.state == Environment.MEDIA_MOUNTED

    private val methodGetPath: Method? by lazy {
        try {
            volumeClass.getMethod("getPath")
        } catch (e: Exception) {
            log(TAG) { "volumeClass.getMethod(\"getPath\")" }
            null
        }
    }

    val path: String?
        get() = try {
            methodGetPath?.invoke(volumeObj) as? String
        } catch (e: ReflectiveOperationException) {
            log(TAG) { "StorageVolume.path reflection failed." }
            directory?.path
        }

    private val methodGetPathFile: Method? by lazy {
        try {
            volumeClass.getMethod("getPathFile")
        } catch (e: Exception) {
            log(TAG) { "volumeClass.getMethod(\"getPathFile\")" }
            null
        }
    }

    private val methodGetUserLabel: Method? by lazy {
        try {
            volumeClass.getMethod("getUserLabel")
        } catch (e: Exception) {
            log(TAG) { "volumeClass.getMethod(\"getUserLabel\")" }
            null
        }
    }

    val userLabel: String?
        get() = try {
            methodGetUserLabel?.invoke(volumeObj) as? String
        } catch (e: ReflectiveOperationException) {
            log(TAG) { "StorageVolume.userLabel reflection failed." }
            null
        }

    private val methodGetDescription: Method? by lazy {
        try {
            volumeClass.getMethod("getDescription", Context::class.java)
        } catch (e: Exception) {
            log(TAG) { " volumeClass.getMethod(\"getDescription\", Context::class.java)" }
            null
        }
    }

    fun getDescription(context: Context?): String? = if (hasApiLevel(30)) {
        volume.getDescription(context)
    } else {
        try {
            try {
                methodGetDescription?.invoke(volumeObj, context) as? String
            } catch (e: Resources.NotFoundException) {
                log(TAG, ERROR) { "Resource not found for description. ${e.asLog()}" }
                null
            }
        } catch (e: ReflectiveOperationException) {
            log(TAG) { "StorageVolume.getDescription reflection failed." }
            null
        }
    }

    private val methodGetOwner: Method? by lazy {
        try {
            volumeClass.getMethod("getOwner")
        } catch (e: Exception) {
            log(TAG) { " volumeClass.getMethod(\"getDescription\", Context::class.java)" }
            null
        }
    }

    val owner: UserHandle?
        get() = try {
            methodGetOwner?.invoke(volumeObj) as? UserHandle
        } catch (e: NoSuchMethodException) {
            if (!hasApiLevel(30)) log(TAG, ERROR) { "StorageVolumeX.getOwner() unavailable." }
            null
        } catch (e: Exception) {
            log(TAG, ERROR) { "StorageVolumeX.getOwner() threw an error: ${e.asLog()}" }
            null
        }

    fun createAccessIntent(directory: String? = null): Intent? {
        return volume.createAccessIntent(directory)
//        return Intent(ACTION_OPEN_EXTERNAL_DIRECTORY).apply {
//            putExtra(EXTRA_STORAGE_VOLUME, volume)
//            putExtra(EXTRA_DIRECTORY_NAME, directory)
//        }
    }

    val rootUri: Uri
        @SuppressLint("NewApi")
        get() = if (hasApiLevel(29)) {
            volume.createOpenDocumentTreeIntent().getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI)!!
        } else {
            DocumentsContract.buildRootUri(
                EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                if (isEmulated) EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID else uuid!!
            )
        }

    val documentUri: Uri
        get() = rootUri.toString()
            .replace("/root/", "/document/")
            .let { Uri.parse(it) }

    val treeUri: Uri
        get() = rootUri.toString()
            .replace("/root/", "/tree/")
            .let { Uri.parse(it) }


    val directory: File?
        @SuppressLint("NewApi")
        get() = if (hasApiLevel(30)) {
            volume.directory
        } else {
            try {
                methodGetPathFile?.invoke(volumeObj) as? File
            } catch (e: ReflectiveOperationException) {
                log(TAG) { "StorageVolume.pathFile reflection failed." }
                null
            }

        }

    fun dump(): String = try {
        val dumpMethod = volumeClass.getMethod("dump")
        dumpMethod.invoke(volumeObj) as String
    } catch (e: Exception) {
        log(TAG, VERBOSE) { "dump() unavailable." }
        this.toString()
    }

    override fun toString(): String {
        val sb = StringBuilder("StorageVolumeX(")
        sb.append("uuid=$uuid, ")
        sb.append("directory=$directory, ")
        sb.append("userlabel=$userLabel, ")
        sb.append("volumeX=$volume, ")
        @SuppressLint("NewApi")
        if (hasApiLevel(29)) {
            sb.append("rootUri=$rootUri")
        }
        sb.append(")")
        return sb.toString()
    }

    companion object {
        val TAG: String = logTag("StorageVolumeX")
        private const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"
        private const val EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID = "primary"
        const val EXTRA_STORAGE_VOLUME = "android.os.storage.extra.STORAGE_VOLUME"
        const val EXTRA_DIRECTORY_NAME = "android.os.storage.extra.DIRECTORY_NAME"
        private const val ACTION_OPEN_EXTERNAL_DIRECTORY = "android.os.storage.action.OPEN_EXTERNAL_DIRECTORY"
    }
}

internal object AnyParceler : Parceler<Any> {
    override fun create(parcel: Parcel): Any = parcel.readParcelable(StorageVolumeX::class.java.classLoader)!!

    override fun Any.write(parcel: Parcel, flags: Int) = parcel.writeParcelable(this as Parcelable, flags)
}

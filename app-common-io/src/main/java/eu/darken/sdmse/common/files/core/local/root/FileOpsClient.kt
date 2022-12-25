package eu.darken.sdmse.common.files.core.local.root

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.getRootCause
import eu.darken.sdmse.common.files.core.Ownership
import eu.darken.sdmse.common.files.core.Permissions
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.LocalPathLookup
import okio.Sink
import okio.Source
import timber.log.Timber
import java.io.IOException
import java.time.Instant

class FileOpsClient @AssistedInject constructor(
    @Assisted private val fileOpsConnection: FileOpsConnection
) : ClientModule {
    fun lookUp(path: LocalPath): LocalPathLookup = try {
        fileOpsConnection.lookUp(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "lookUp(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun listFiles(path: LocalPath): List<LocalPath> = try {
        fileOpsConnection.listFiles(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "listFiles(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun lookupFiles(path: LocalPath): List<LocalPathLookup> = try {
        fileOpsConnection.lookupFiles(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "lookupFiles(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun readFile(path: LocalPath): Source = try {
        fileOpsConnection.readFile(path).source()
    } catch (e: IOException) {
        Timber.tag(TAG).e(e, "readFile(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun writeFile(path: LocalPath): Sink = try {
        fileOpsConnection.writeFile(path).sink()
    } catch (e: IOException) {
        Timber.tag(TAG).e(e, "writeFile(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun mkdirs(path: LocalPath): Boolean = try {
        fileOpsConnection.mkdirs(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "mkdirs(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun createNewFile(path: LocalPath): Boolean = try {
        fileOpsConnection.createNewFile(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "mkdirs(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun canRead(path: LocalPath): Boolean = try {
        fileOpsConnection.canRead(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "path(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun canWrite(path: LocalPath): Boolean = try {
        fileOpsConnection.canWrite(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "canWrite(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun exists(path: LocalPath): Boolean = try {
        fileOpsConnection.exists(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "exists(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun delete(path: LocalPath): Boolean = try {
        fileOpsConnection.delete(path)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "delete(path=$path) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean = try {
        fileOpsConnection.createSymlink(linkPath, targetPath)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "createSymlink(linkPath=$linkPath, targetPath=$targetPath) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun setModifiedAt(path: LocalPath, modifiedAt: Instant): Boolean = try {
        fileOpsConnection.setModifiedAt(path, modifiedAt.toEpochMilli())
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "setModifiedAt(path=$path, modifiedAt=$modifiedAt) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun setPermissions(path: LocalPath, permissions: Permissions): Boolean = try {
        fileOpsConnection.setPermissions(path, permissions)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "setPermissions(path=$path, permissions=$permissions) failed.")
        throw fakeIOException(e.getRootCause())
    }

    fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = try {
        fileOpsConnection.setOwnership(path, ownership)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "setOwnership(path=$path, ownership=$ownership) failed.")
        throw fakeIOException(e.getRootCause())
    }

    private fun fakeIOException(e: Throwable): IOException {
        val gulpExceptionPrefix = "java.io.IOException: "
        val message = when {
            e.message.isNullOrEmpty() -> e.toString()
            e.message?.startsWith(gulpExceptionPrefix) == true -> e.message!!.replace(gulpExceptionPrefix, "")
            else -> ""
        }
        return IOException(message, e.cause)
    }

    companion object {
        val TAG = logTag("Root", "Java", "FileOps", "Client")
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: FileOpsConnection): FileOpsClient
    }
}
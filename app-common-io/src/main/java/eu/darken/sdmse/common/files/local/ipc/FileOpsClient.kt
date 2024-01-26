package eu.darken.sdmse.common.files.local.ipc

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.getRootCause
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.ipc.sink
import eu.darken.sdmse.common.ipc.source
import okio.Sink
import okio.Source
import java.io.IOException
import java.time.Instant

class FileOpsClient @AssistedInject constructor(
    @Assisted private val fileOpsConnection: FileOpsConnection
) : IpcClientModule {

    fun listFiles(path: LocalPath): Collection<LocalPath> = try {
        fileOpsConnection.listFiles(path).also {
            if (Bugs.isTrace) log(TAG) { "listFiles($path): $it" }
        }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun listFilesStream(path: LocalPath): Collection<LocalPath> = try {
        fileOpsConnection.listFilesStream(path).toLocalPaths().also {
            if (Bugs.isTrace) log(TAG) { "listFilesStream($path) finished streaming, ${it.size} items" }
        }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun lookupFiles(path: LocalPath): Collection<LocalPathLookup> = try {
        fileOpsConnection.lookupFiles(path).also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFiles($path): $it" }
        }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun lookUp(path: LocalPath): LocalPathLookup = try {
        fileOpsConnection.lookUp(path).also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookup($path): $it" }
        }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun lookupFilesStream(path: LocalPath): Collection<LocalPathLookup> = try {
        fileOpsConnection.lookupFilesStream(path).toLocalPathLookups().also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFilesStream($path) finished streaming, ${it.size} items" }
        }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun lookupFilesExtendedStream(path: LocalPath): Collection<LocalPathLookupExtended> = try {
        fileOpsConnection.lookupFilesExtendedStream(path).toLocalPathLookupExtended().also {
            if (Bugs.isTrace) log(
                TAG,
                VERBOSE
            ) { "lookupFilesExtendedStream($path) finished streaming, ${it.size} items" }
        }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun walk(
        path: LocalPath,
        options: APathGateway.WalkOptions<LocalPath, LocalPathLookup>,
    ): Collection<LocalPathLookup> = try {
        if (!options.isDirect) throw IllegalArgumentException("Only direct walk options are supported")
        fileOpsConnection
            .walkStream(
                path,
                (options.pathDoesNotContain ?: emptyList()).toMutableList(),
            )
            .toLocalPathLookups()
            .also {
                if (Bugs.isTrace) log(TAG, VERBOSE) { "walk($path) finished streaming, ${it.size} items" }
            }
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun readFile(path: LocalPath): Source = try {
        fileOpsConnection.readFile(path).source()
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun writeFile(path: LocalPath): Sink = try {
        fileOpsConnection.writeFile(path).sink()
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun mkdirs(path: LocalPath): Boolean = try {
        fileOpsConnection.mkdirs(path)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun createNewFile(path: LocalPath): Boolean = try {
        fileOpsConnection.createNewFile(path)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun canRead(path: LocalPath): Boolean = try {
        fileOpsConnection.canRead(path)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun canWrite(path: LocalPath): Boolean = try {
        fileOpsConnection.canWrite(path)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun exists(path: LocalPath): Boolean = try {
        fileOpsConnection.exists(path)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun delete(path: LocalPath): Boolean = try {
        fileOpsConnection.delete(path)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean = try {
        fileOpsConnection.createSymlink(linkPath, targetPath)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun setModifiedAt(path: LocalPath, modifiedAt: Instant): Boolean = try {
        fileOpsConnection.setModifiedAt(path, modifiedAt.toEpochMilli())
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun setPermissions(path: LocalPath, permissions: Permissions): Boolean = try {
        fileOpsConnection.setPermissions(path, permissions)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = try {
        fileOpsConnection.setOwnership(path, ownership)
    } catch (e: Exception) {
        throw e.toFakeIOException()
    }

    private fun Exception.toFakeIOException(): IOException {
        val org = this.getRootCause()
        val gulpPrefix = "java.io.IOException: "
        val message = when {
            org.message.isNullOrEmpty() -> org.toString()
            org.message?.startsWith(gulpPrefix) == true -> org.message!!.replace(gulpPrefix, "")
            else -> ""
        }
        return IOException(message, org.cause)
    }

    companion object {
        val TAG = logTag("FileOps", "Service", "Client")
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: FileOpsConnection): FileOpsClient
    }
}
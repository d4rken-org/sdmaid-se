package eu.darken.sdmse.common.files.local.ipc

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathGateway
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.ipc.fileHandle
import kotlinx.coroutines.flow.Flow
import okio.FileHandle
import java.time.Instant

class FileOpsClient @AssistedInject constructor(
    @Assisted private val fileOpsConnection: FileOpsConnection
) : IpcClientModule {

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun listFiles(path: LocalPath): Collection<LocalPath> = try {
        fileOpsConnection.listFilesStream(path).toLocalPaths().also {
            if (Bugs.isTrace) log(TAG) { "listFiles($path) finished streaming, ${it.size} items" }
        }
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun lookUp(path: LocalPath): LocalPathLookup = try {
        fileOpsConnection.lookUp(path).also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookup($path): $it" }
        }
    } catch (e: Exception) {
        throw e.refineException()
    }

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun lookupFiles(path: LocalPath): Collection<LocalPathLookup> = try {
        fileOpsConnection.lookupFilesStream(path).toLocalPathLookups().also {
            if (Bugs.isTrace) log(TAG, VERBOSE) { "lookupFiles($path) finished streaming, ${it.size} items" }
        }
    } catch (e: Exception) {
        throw e.refineException()
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
        throw e.refineException()
    }

    /**
     * Doesn't run into IPC buffer overflows on large directories
     */
    fun walk(
        path: LocalPath,
        options: APathGateway.WalkOptions<LocalPath, LocalPathLookup>,
    ): Flow<LocalPathLookup> {
        if (!options.isDirect) throw IllegalArgumentException("Only direct walk options are supported")

        val output = try {
            fileOpsConnection.walkStream(
                path,
                (options.pathDoesNotContain ?: emptyList()).toMutableList(),
            )
        } catch (e: Exception) {
            throw e.refineException()
        }
        return output.toLocalPathLookupFlow()
    }

    fun du(path: LocalPath): Long = try {
        fileOpsConnection.du(path)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun file(path: LocalPath, readWrite: Boolean): FileHandle = try {
        fileOpsConnection.file(path, readWrite).fileHandle(readWrite)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun mkdirs(path: LocalPath): Boolean = try {
        fileOpsConnection.mkdirs(path)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun createNewFile(path: LocalPath): Boolean = try {
        fileOpsConnection.createNewFile(path)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun canRead(path: LocalPath): Boolean = try {
        fileOpsConnection.canRead(path)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun canWrite(path: LocalPath): Boolean = try {
        fileOpsConnection.canWrite(path)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun exists(path: LocalPath): Boolean = try {
        fileOpsConnection.exists(path)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun delete(path: LocalPath, recursive: Boolean, dryRun: Boolean): Boolean = try {
        fileOpsConnection.delete(path, recursive, dryRun)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun createSymlink(linkPath: LocalPath, targetPath: LocalPath): Boolean = try {
        fileOpsConnection.createSymlink(linkPath, targetPath)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun setModifiedAt(path: LocalPath, modifiedAt: Instant): Boolean = try {
        fileOpsConnection.setModifiedAt(path, modifiedAt.toEpochMilli())
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun setPermissions(path: LocalPath, permissions: Permissions): Boolean = try {
        fileOpsConnection.setPermissions(path, permissions)
    } catch (e: Exception) {
        throw e.refineException()
    }

    fun setOwnership(path: LocalPath, ownership: Ownership): Boolean = try {
        fileOpsConnection.setOwnership(path, ownership)
    } catch (e: Exception) {
        throw e.refineException()
    }

    companion object {
        val TAG = logTag("FileOps", "Service", "Client")
    }

    @AssistedFactory
    interface Factory {
        fun create(connection: FileOpsConnection): FileOpsClient
    }
}
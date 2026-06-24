package eu.darken.sdmse.common.files.core.local

import android.system.Os
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files

fun File(vararg crumbs: String): File {
    var compacter = File(crumbs[0])
    for (i in 1 until crumbs.size) {
        compacter = File(compacter, crumbs[i])
    }
    return compacter
}

fun File.tryMkDirs(): File {
    if (exists()) {
        if (isDirectory) {
            log(VERBOSE) { "Directory already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Directory exists, but is not a directory: $this")
        }
    }

    if (mkdirs()) {
        log(VERBOSE) { "Directory created: $this" }
        return this
    } else {
        throw IllegalStateException("Couldn't create Directory: $this")
    }
}

fun File.tryMkFile(): File {
    if (exists()) {
        if (isFile) {
            log(VERBOSE) { "File already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Path exists but is not a file: $this")
        }
    }

    if (parentFile?.exists() == false) parentFile?.tryMkDirs()

    if (createNewFile()) {
        log(VERBOSE) { "File created: $this" }
        return this
    } else {
        throw IllegalStateException("Couldn't create file: $this")
    }
}

fun File.deleteRecursivelySafe(): Boolean {
    // readLink() is fail-open: returns null on any error, treating the path as "not a symlink".
    // Safe because: as root (the dangerous context), readlink never fails on permission;
    // as non-root, permission errors also block listFiles()/delete(), so recursion goes nowhere.
    val linkTarget = readLink()
    if (linkTarget != null) {
        log(WARN) { "deleteRecursivelySafe(): Symlink detected, deleting link only: $this -> $linkTarget" }
        return delete()
    }
    var success = true
    if (isDirectory) {
        val children = listFiles()
        if (children != null) {
            for (child in children) {
                if (!child.deleteRecursivelySafe()) success = false
            }
        }
    }
    return if (success) delete() else false
}

fun File.listFiles2(): List<File> {
    if (!exists()) throw IOException("File does not exist")
    if (!canRead()) throw IOException("Can't read $path")
    return this.listFiles()?.toList() ?: throw IOException("Failed to listFiles() on $path")
}

/**
 * Lazy counterpart to [listFiles2]: enumerates the directory via [Files.newDirectoryStream] instead
 * of materializing the whole child array up front, so a single huge directory doesn't spike the heap.
 * The underlying [java.nio.file.DirectoryStream] stays open for the duration of flow collection and is
 * closed when collection completes, throws, or is cancelled.
 *
 * All failures are normalized to [IOException] to match [listFiles2]'s contract and to survive the IPC
 * error round-trip (which reconstructs exceptions by class name). In particular [DirectoryIteratorException]
 * is an unchecked wrapper around an [IOException] thrown during iteration and must not leak as-is.
 *
 * Entry order is unspecified, same as [listFiles2].
 */
fun File.listFilesStreaming(): Flow<File> = flow {
    if (!exists()) throw IOException("File does not exist")
    if (!canRead()) throw IOException("Can't read $path")
    val stream = try {
        Files.newDirectoryStream(toPath())
    } catch (e: IOException) {
        // NotDirectoryException / NoSuchFileException / AccessDeniedException etc. — match listFiles2's
        // generic failure so callers and the IPC round-trip see a plain IOException.
        throw IOException("Failed to listFiles() on $path", e)
    }
    stream.use { dirStream ->
        try {
            for (entry in dirStream) emit(entry.toFile())
        } catch (e: DirectoryIteratorException) {
            throw (e.cause as? IOException) ?: IOException("Failed to listFiles() on $path", e)
        }
    }
}

fun File.isSymbolicLink(): Boolean {
    return readLink() != null
}

fun File.createSymlink(target: File): Boolean {
    Os.symlink(target.path, this.path)
    return this.exists()
}

fun File.readLink(): String? = try {
    Os.readlink(this.path)
} catch (_: Exception) {
    null
}

fun File.isReadable(): Boolean = try {
    if (isDirectory) {
        canRead()
    } else {
        // canRead() may return true, while SELinux blocks open
        // type=1400 audit(0.0:12576): avc: denied { open } for path="/data/data/alinktests/subdir/symtarget" dev="sda45" ino=2754227 scontext=u:r:untrusted_app_27:s0:c100,c257,c512,c768 tcontext=u:object_r:system_data_file:s0 tclass=file permissive=0
        reader().use { it.read() }
        true
    }
} catch (_: Exception) {
    false
}

val File.parents: Sequence<File>
    get() = sequence {
        var parent = parentFile
        while (parent != null) {
            yield(parent)
            parent = parent.parentFile
        }
    }

val File.parentsInclusive: Sequence<File>
    get() = sequenceOf(this) + parents
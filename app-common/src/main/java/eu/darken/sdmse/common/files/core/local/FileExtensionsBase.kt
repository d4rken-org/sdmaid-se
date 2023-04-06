package eu.darken.sdmse.common.files.local

import android.system.Os
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

@Suppress("FunctionName")
fun File(vararg crumbs: String): File {
    var compacter = File(crumbs[0])
    for (i in 1 until crumbs.size) {
        compacter = File(compacter, crumbs[i])
    }
    return compacter
}

fun File.requireExists(): File {
    if (!exists()) {
        throw IllegalStateException("Path doesn't exist, but should: $this")
    }
    return this
}

fun File.requireNotExists(): File {
    if (exists()) {
        throw IllegalStateException("Path exist, but shouldn't: $this")
    }
    return this
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

@Throws(IOException::class)
fun File.deleteAll() {
    if (isDirectory) {
        listFiles()?.forEach { it.deleteAll() }
    }
    if (delete()) {
        log(VERBOSE) { "File.release(): Deleted $this" }
    } else if (!exists()) {
        log(WARN) { "File.release(): File didn't exist: $this" }
    } else {
        throw FileNotFoundException("Failed to delete file: $this")
    }
}

fun File.listFiles2(): List<File> {
    if (!exists()) throw IOException("File does not exist")
    if (!canRead()) throw IOException("Can't read $path")
    return this.listFiles()?.toList() ?: throw IOException("Failed to listFiles() on $path")
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
} catch (e: Exception) {
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
} catch (e: Exception) {
    false
}

fun File.canReadExecute(): Boolean = canRead() && canExecute()

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

fun String.fixSlashes(): String = replace("/", File.separator)
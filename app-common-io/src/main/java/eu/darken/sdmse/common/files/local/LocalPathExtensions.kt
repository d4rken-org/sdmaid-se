package eu.darken.sdmse.common.files.local

import android.system.Os
import android.system.StructStat
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant


fun LocalPath.crumbsTo(child: LocalPath): Array<String> {
    val childPath = child.path
    val parentPath = this.path
    val pure = childPath.replaceFirst(parentPath, "")
    return pure.split(File.separatorChar)
        .filter { it.isNotEmpty() }
        .toTypedArray()
}

fun LocalPath.toCrumbs(): List<LocalPath> {
    val crumbs = mutableListOf<LocalPath>()
    crumbs.add(this)
    var parent = this.asFile().parentFile
    while (parent != null) {
        crumbs.add(0, LocalPath.build(parent))
        parent = parent.parentFile
    }
    return crumbs
}

fun LocalPath.performLookup(): LocalPathLookup {
    // One lstat (NOFOLLOW) yields type + size + mtime, replacing ~6 java.io.File syscalls per entry.
    // NOFOLLOW means a symlink reports its OWN size/mtime (not the target's) — which is correct, since
    // deletion only ever removes the link, never the target.
    val attrs = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (e: IOException) {
        throw ReadException("Does not exist or can't be read", this, e)
    } catch (e: InvalidPathException) {
        // file.toPath() rejects paths with illegal chars (e.g. an embedded NUL). The old
        // java.io.File-based lookup returned null here, which became a ReadException — keep that contract.
        throw ReadException("Does not exist or can't be read", this, e)
    }

    val type = when {
        attrs.isSymbolicLink -> FileType.SYMBOLIC_LINK
        attrs.isDirectory -> FileType.DIRECTORY
        attrs.isRegularFile -> FileType.FILE
        else -> FileType.UNKNOWN
    }

    return LocalPathLookup(
        fileType = type,
        lookedUp = this,
        size = attrs.size(),
        modifiedAt = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()),
        target = if (type == FileType.SYMBOLIC_LINK) file.resolveSymlinkTarget() else null,
    )
}

/**
 * Resolves a symlink's target to an absolute [LocalPath]. Uses NIO so it works in JVM tests (unlike
 * `Os.readlink`), and resolves a relative target against the link's parent (the old
 * `LocalPath.build(rawTarget)` turned `../x` into a bogus `/../x`). The result is lexically
 * normalized (collapses `.`/`..`); it is informational only and not used for deletion.
 */
private fun File.resolveSymlinkTarget(): LocalPath? {
    val raw = try {
        Files.readSymbolicLink(toPath())
    } catch (e: Exception) {
        return null
    }
    val resolved = if (raw.isAbsolute) raw else (toPath().parent?.resolve(raw) ?: raw)
    return LocalPath.build(resolved.normalize().toString())
}

fun LocalPath.performLookupExtended(
    ipcFunnel: IPCFunnel,
    libcoreTool: LibcoreTool,
): LocalPathLookupExtended {

    val lookup = this.performLookup()

    val fstat: StructStat? = try {
        Os.lstat(file.path)
    } catch (e: Exception) {
        log(LocalGateway.TAG, WARN) { "fstat failed on $this: ${e.asLog()}" }
        null
    }

    val ownership = fstat?.let {
        val uid = it.st_uid
        val gid = it.st_gid

        val userName: String? = libcoreTool.getNameForUid(uid)
        val groupName: String? = libcoreTool.getNameForGid(gid)

        // TODO use Files.readAttributes as fallback?

        Ownership(uid, gid, userName, groupName)
    }

    return LocalPathLookupExtended(
        lookup = lookup,
        ownership = ownership,
        permissions = fstat?.let { Permissions(it.st_mode) },
    )
}

fun LocalPath.isAncestorOf(child: LocalPath): Boolean {
    val parentPath = this.asFile().path
    val childPath = child.asFile().path

    return when {
        parentPath.length >= childPath.length -> false
        !childPath.startsWith(parentPath) -> false
        parentPath == File.separator -> true
        else -> childPath[parentPath.length] == File.separatorChar
    }
}

fun LocalPath.isParentOf(child: LocalPath): Boolean {
    return isAncestorOf(child) && child(child.name) == child
}

fun LocalPath.startsWith(prefix: LocalPath): Boolean {
    if (this == prefix) return true
    if (segments.size < prefix.segments.size) return false

    return when {
        prefix.segments.size == 1 -> {
            segments.first().startsWith(prefix.segments.first())
        }

        segments.size == prefix.segments.size -> {
            val match = prefix.segments.dropLast(1) == segments.dropLast(1)
            match && segments.last().startsWith(prefix.segments.last())
        }

        else -> {
            val match = prefix.segments.dropLast(1) == segments.dropLast(segments.size - prefix.segments.size + 1)
            match && segments[prefix.segments.size - 1].startsWith(prefix.segments.last())
        }
    }
}

fun LocalPath.removePrefix(prefix: LocalPath, overlap: Int = 0): Segments {
    if (!startsWith(prefix)) throw IllegalArgumentException("$prefix is not a prefix of $this")
    return segments.drop(prefix.segments.size - overlap)
}

private val WELL_KNOWN_ANDROID_SUBDIRS = setOf("data", "obb", "media")

/**
 * Returns true if this path is under an uncommon `Android/` subdirectory,
 * i.e. not `data`, `obb`, or `media`.
 */
val LocalPath.isUncommonAndroidDir: Boolean
    get() {
        val segs = segments
        val idx = segs.indexOf("Android")
        if (idx < 0 || idx + 1 >= segs.size) return false
        return segs[idx + 1] !in WELL_KNOWN_ANDROID_SUBDIRS
    }
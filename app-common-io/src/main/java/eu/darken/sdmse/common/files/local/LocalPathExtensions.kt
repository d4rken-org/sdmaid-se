package eu.darken.sdmse.common.files.local

import android.system.Os
import android.system.StructStat
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.asFile
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.pkgops.LibcoreTool
import java.io.File
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
    val type = file.getAPathFileType() ?: throw ReadException(this, "Does not exist or can't be read")

    return LocalPathLookup(
        fileType = type,
        lookedUp = this,
        size = file.length(),
        modifiedAt = Instant.ofEpochMilli(file.lastModified()),

        target = file.readLink()?.let { LocalPath.Companion.build(it) }
    )
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
    val parentPath = this.asFile().absolutePath
    val childPath = child.asFile().absolutePath

    return when (parentPath) {
        childPath -> false
        File.separator -> childPath.startsWith(parentPath)
        else -> childPath.startsWith(parentPath + File.separator)
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
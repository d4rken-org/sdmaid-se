package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.crumbsTo
import eu.darken.sdmse.common.files.core.local.isAncestorOf
import eu.darken.sdmse.common.files.core.local.isParentOf
import eu.darken.sdmse.common.files.core.local.removePrefix
import eu.darken.sdmse.common.files.core.local.startsWith
import eu.darken.sdmse.common.files.core.saf.*
import eu.darken.sdmse.common.files.core.saf.crumbsTo
import eu.darken.sdmse.common.files.core.saf.isAncestorOf
import eu.darken.sdmse.common.files.core.saf.isParentOf
import eu.darken.sdmse.common.files.core.saf.removePrefix
import eu.darken.sdmse.common.files.core.saf.startsWith
import okio.Sink
import okio.Source
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.*

fun APath.crumbsTo(child: APath): Array<String> {
    require(this.pathType == child.pathType)

    return when (pathType) {
        APath.PathType.RAW -> (this as RawPath).crumbsTo(child as RawPath)
        APath.PathType.LOCAL -> (this as LocalPath).crumbsTo(child as LocalPath)
        APath.PathType.SAF -> (this as SAFPath).crumbsTo(child as SAFPath)
    }
}

@Suppress("UNCHECKED_CAST")
fun <P : APath> P.childCast(vararg segments: String): P = child(*segments) as P

fun APath.asFile(): File = when (this) {
    is LocalPath -> this.file
    else -> File(this.path)
}

fun <P : APath, PL : APathLookup<P>, GT : APathGateway<P, PL>> P.walk(
    gateway: GT,
    filter: (PL) -> Boolean = { true }
): PathTreeFlow<P, PL, GT> {
    return PathTreeFlow(gateway, downCast(), filter)
}

/**
 * // FIXME not sure if this can be fixed?
 * APathLookup is a super of APath, but LocalPathLookup is not a super of LocalPath
 * The compiler allows us to pass a LocalPathLookup to a function that only takesk LocalPath
 */
internal fun <T : APath> T.downCast(): T = if (this is APathLookup<*>) {
    @Suppress("UNCHECKED_CAST")
    this.lookedUp as T
} else {
    this
}

suspend fun <T : APath> T.exists(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.exists(downCast())
}

suspend fun <T : APath> T.requireExists(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (!exists(gateway)) {
        throw IllegalStateException("Path doesn't exist, but should: $this")
    }
    return this
}

suspend fun <T : APath> T.requireNotExists(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (exists(gateway)) {
        throw IllegalStateException("Path exist, but shouldn't: $this")
    }
    return this
}

suspend fun <T : APath> T.createFileIfNecessary(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (exists(gateway)) {
        if (gateway.lookup(downCast()).fileType == FileType.FILE) {
            log(VERBOSE) { "File already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Exists, but is not a file: $this")
        }
    }

    gateway.createFile(downCast())
    log(VERBOSE) { "File created: $this" }
    return this
}

suspend fun <T : APath> T.createDirIfNecessary(gateway: APathGateway<T, out APathLookup<T>>): T {
    if (exists(gateway)) {
        if (gateway.lookup(downCast()).isDirectory) {
            log(VERBOSE) { "Directory already exists, not creating: $this" }
            return this
        } else {
            throw IllegalStateException("Exists, but is not a directory: $this")
        }
    }

    gateway.createDir(downCast())
    log(VERBOSE) { "Directory created: $this" }
    return this
}

suspend fun <T : APath> T.delete(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return if (gateway.delete(this)) {
        log(VERBOSE) { "APath.delete(): Deleted $this" }
        true
    } else if (!exists(gateway)) {
        log(WARN) { "APath.delete(): File didn't exist: $this" }
        true
    } else {
        throw FileNotFoundException("Failed to delete file: $this")
    }
}

suspend fun <T : APath> T.deleteAll(gateway: APathGateway<T, out APathLookup<T>>) {
    if (gateway.lookup(downCast()).isDirectory) {
        gateway.listFiles(downCast()).forEach { it.deleteAll(gateway) }
    }
    if (gateway.delete(this)) {
        log(VERBOSE) { "APath.deleteAll(): Deleted $this" }
    } else if (!exists(gateway)) {
        log(WARN) { "APath.deleteAll(): File didn't exist: $this" }
    } else {
        throw FileNotFoundException("Failed to delete file: $this")
    }
}

suspend fun <T : APath> T.write(gateway: APathGateway<T, out APathLookup<T>>): Sink {
    return gateway.write(downCast())
}

suspend fun <T : APath> T.read(gateway: APathGateway<T, out APathLookup<T>>): Source {
    return gateway.read(downCast())
}

suspend fun <T : APath> T.createSymlink(gateway: APathGateway<T, out APathLookup<T>>, target: T): Boolean {
    return gateway.createSymlink(downCast(), target)
}

suspend fun <T : APath> T.setModifiedAt(gateway: APathGateway<T, out APathLookup<T>>, modifiedAt: Instant): Boolean {
    return gateway.setModifiedAt(downCast(), modifiedAt)
}

suspend fun <T : APath> T.setPermissions(
    gateway: APathGateway<T, out APathLookup<T>>,
    permissions: Permissions
): Boolean {
    return gateway.setPermissions(downCast(), permissions)
}

suspend fun <T : APath> T.setOwnership(gateway: APathGateway<T, out APathLookup<T>>, ownership: Ownership): Boolean {
    return gateway.setOwnership(downCast(), ownership)
}

suspend fun <P : APath, PLU : APathLookup<P>> P.lookup(gateway: APathGateway<P, PLU>): PLU {
    return gateway.lookup(downCast())
}

suspend fun <P : APath, PLU : APathLookup<P>> P.lookupFiles(gateway: APathGateway<P, PLU>): Collection<PLU> {
    return gateway.lookupFiles(downCast())
}

suspend fun <P : APath, PLU : APathLookup<P>> P.lookupFilesOrNull(gateway: APathGateway<P, PLU>): Collection<PLU>? {
    return if (exists(gateway)) gateway.lookupFiles(downCast()) else null
}

suspend fun <T : APath> T.listFiles(gateway: APathGateway<T, out APathLookup<T>>): Collection<T> {
    return gateway.listFiles(downCast())
}

suspend fun <T : APath> T.listFilesOrNull(gateway: APathGateway<T, out APathLookup<T>>): Collection<T>? {
    return if (exists(gateway)) gateway.listFiles(downCast()) else null
}

suspend fun <T : APath> T.canRead(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.canRead(downCast())
}

suspend fun <T : APath> T.canWrite(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.canWrite(downCast())
}

suspend fun <T : APath> T.isFile(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.lookup(downCast()).fileType == FileType.FILE
}

suspend fun <T : APath> T.isDirectory(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.lookup(downCast()).fileType == FileType.DIRECTORY
}

suspend fun <T : APath> T.mkdirs(gateway: APathGateway<T, out APathLookup<T>>): Boolean {
    return gateway.createDir(downCast())
}

fun APath.isAncestorOf(descendant: APath): Boolean {
    if (this.pathType != descendant.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).isAncestorOf(descendant.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).isAncestorOf(descendant.downCast() as SAFPath)
        APath.PathType.RAW -> descendant.downCast().path.startsWith(this.downCast().path + "/")
    }
}

fun APath.isDescendantOf(ancestor: APath): Boolean {
    if (this.pathType != ancestor.pathType) return false
    return ancestor.isAncestorOf(this)
}

/**
 * A parent is a DIRECT ancestor
 * See [isAncestorOf]
 */
fun APath.isParentOf(child: APath): Boolean {
    if (this.pathType != child.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).isParentOf(child.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).isParentOf(child.downCast() as SAFPath)
        APath.PathType.RAW -> this.downCast().child(child.name) == child
    }
}

fun APath.isChildOf(parent: APath): Boolean {
    if (this.pathType != parent.pathType) return false
    return parent.isParentOf(this)
}

fun APath.matches(other: APath): Boolean {
    if (this.pathType != other.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).path == (other.downCast() as LocalPath).path
        APath.PathType.SAF -> (this.downCast() as SAFPath).path == (other.downCast() as SAFPath).path
        APath.PathType.RAW -> other.downCast().path == this.downCast().path
    }
}

fun APath.containsSegments(vararg target: String): Boolean {
    return Collections.indexOfSubList(this.segments, target.toList()) != -1
}

fun APath.startsWith(prefix: APath): Boolean {
    if (this.pathType != prefix.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).startsWith(prefix.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).startsWith(prefix.downCast() as SAFPath)
        APath.PathType.RAW -> this.downCast().path.startsWith(prefix.downCast().path)
    }
}

fun APath.removePrefix(prefix: APath): List<String> {
    if (this.pathType != prefix.pathType) {
        throw IllegalArgumentException("Can't compare different types ($this and $prefix)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this.downCast() as LocalPath).removePrefix(prefix.downCast() as LocalPath)
        APath.PathType.SAF -> (this.downCast() as SAFPath).removePrefix(prefix.downCast() as SAFPath)
        APath.PathType.RAW -> this.downCast().segments.drop(prefix.downCast().segments.size)
    }
}
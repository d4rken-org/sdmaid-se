package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.local.*
import eu.darken.sdmse.common.files.local.crumbsTo
import eu.darken.sdmse.common.files.local.isAncestorOf
import eu.darken.sdmse.common.files.local.isParentOf
import eu.darken.sdmse.common.files.local.startsWith
import eu.darken.sdmse.common.files.saf.*
import eu.darken.sdmse.common.files.saf.crumbsTo
import eu.darken.sdmse.common.files.saf.isAncestorOf
import eu.darken.sdmse.common.files.saf.isParentOf
import eu.darken.sdmse.common.files.saf.startsWith
import kotlinx.coroutines.flow.Flow
import okio.Sink
import okio.Source
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.*
import eu.darken.sdmse.common.files.local.removePrefix as removePrefixLocalPath
import eu.darken.sdmse.common.files.saf.removePrefix as removePrefixSafPath

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

suspend fun <P : APath, PL : APathLookup<P>, PLE : APathLookupExtended<P>, GT : APathGateway<P, PL, PLE>> P.walk(
    gateway: GT,
    filter: (suspend (PL) -> Boolean)? = null
): Flow<PL> {
    return PathTreeFlow(gateway, this, filter ?: { true })
}

suspend fun <T : APath> T.exists(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Boolean {
    return gateway.exists(this)
}

suspend fun <T : APath> T.requireExists(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): T {
    if (!exists(gateway)) {
        throw IllegalStateException("Path doesn't exist, but should: $this")
    }
    return this
}

suspend fun <T : APath> T.requireNotExists(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): T {
    if (exists(gateway)) {
        throw IllegalStateException("Path exist, but shouldn't: $this")
    }
    return this
}

suspend fun <T : APath> T.createFileIfNecessary(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): T {
    if (exists(gateway)) {
        if (gateway.lookup(this).fileType == FileType.FILE) {
            log(VERBOSE) { "File already exists, not creating: $this" }
            return this
        } else {
            throw IOException("Exists, but is not a file: $this")
        }
    }

    return createFile(gateway)
}

suspend fun <T : APath> T.createFile(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): T {
    gateway.createFile(this)
    log(VERBOSE) { "File created: $this" }
    return this
}

suspend fun <T : APath> T.createDirIfNecessary(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): T {
    if (exists(gateway)) {
        if (gateway.lookup(this).isDirectory) {
            log(VERBOSE) { "Directory already exists, not creating: $this" }
            return this
        } else {
            throw IOException("Exists, but is not a directory: $this")
        }
    }

    gateway.createDir(this)
    log(VERBOSE) { "Directory created: $this" }
    return this
}

suspend fun <T : APath> T.delete(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>) {
    gateway.delete(this)
    log(VERBOSE) { "APath.delete(): Deleted $this" }
}

// TODO move this into the gateways?
suspend fun <T : APath> T.deleteAll(
    gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>,
    filter: (APathLookup<*>) -> Boolean = { true }
) {
    try {
        // Recursion enter
        val lookup = gateway.lookup(this)

        if (lookup.isDirectory) {
            gateway.listFiles(this).forEach { it.deleteAll(gateway, filter) }
        }

        if (!filter(lookup)) {
            log(VERBOSE) { "Skipped due to filter: $this" }
            return
        }
    } catch (e: ReadException) {
        if (!gateway.exists(this)) return else throw e
    }

    // Recursion exit
    this.delete(gateway)
}

suspend fun <T : APath> T.write(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Sink {
    return gateway.write(this)
}

suspend fun <T : APath> T.read(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Source {
    return gateway.read(this)
}

suspend fun <T : APath> T.createSymlink(
    gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>,
    target: T
): Boolean {
    return gateway.createSymlink(this, target)
}

suspend fun <T : APath> T.setModifiedAt(
    gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>,
    modifiedAt: Instant
): Boolean {
    return gateway.setModifiedAt(this, modifiedAt)
}

suspend fun <T : APath> T.setPermissions(
    gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>,
    permissions: Permissions
): Boolean {
    return gateway.setPermissions(this, permissions)
}

suspend fun <T : APath> T.setOwnership(
    gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>,
    ownership: Ownership
): Boolean {
    return gateway.setOwnership(this, ownership)
}

suspend fun <P : APath, PL : APathLookup<P>, PLE : APathLookupExtended<P>> P.lookup(gateway: APathGateway<P, PL, PLE>): PL {
    return gateway.lookup(this)
}

suspend fun <P : APath, PL : APathLookup<P>, PLE : APathLookupExtended<P>> P.lookupFiles(gateway: APathGateway<P, PL, PLE>): Collection<PL> {
    return gateway.lookupFiles(this)
}

suspend fun <P : APath, PL : APathLookup<P>, PLE : APathLookupExtended<P>> P.lookupFilesOrNull(gateway: APathGateway<P, PL, PLE>): Collection<PL>? {
    return if (exists(gateway)) gateway.lookupFiles(this) else null
}

suspend fun <T : APath> T.listFiles(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Collection<T> {
    return gateway.listFiles(this)
}

suspend fun <T : APath> T.canRead(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Boolean {
    return gateway.canRead(this)
}

suspend fun <T : APath> T.canWrite(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Boolean {
    return gateway.canWrite(this)
}

suspend fun <T : APath> T.isFile(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Boolean {
    return gateway.lookup(this).fileType == FileType.FILE
}

suspend fun <T : APath> T.isDirectory(gateway: APathGateway<T, out APathLookup<T>, out APathLookupExtended<T>>): Boolean {
    return gateway.lookup(this).fileType == FileType.DIRECTORY
}

fun APath.isAncestorOf(descendant: APath): Boolean {
    if (this.pathType != descendant.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this as LocalPath).isAncestorOf(descendant as LocalPath)
        APath.PathType.SAF -> (this as SAFPath).isAncestorOf(descendant as SAFPath)
        APath.PathType.RAW -> descendant.path.startsWith(this.path + "/")
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
        APath.PathType.LOCAL -> (this as LocalPath).isParentOf(child as LocalPath)
        APath.PathType.SAF -> (this as SAFPath).isParentOf(child as SAFPath)
        APath.PathType.RAW -> this.child(child.name) == child
    }
}

fun APath.isChildOf(parent: APath): Boolean {
    if (this.pathType != parent.pathType) return false
    return parent.isParentOf(this)
}

fun APath.matches(other: APath): Boolean {
    if (this.pathType != other.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this as LocalPath).path == (other as LocalPath).path
        APath.PathType.SAF -> (this as SAFPath).path == (other as SAFPath).path
        APath.PathType.RAW -> other.path == this.path
    }
}

fun APath.containsSegments(vararg target: String): Boolean {
    return Collections.indexOfSubList(this.segments, target.toList()) != -1
}

fun APath.startsWith(prefix: APath): Boolean {
    if (this.pathType != prefix.pathType) return false
    return when (pathType) {
        APath.PathType.LOCAL -> (this as LocalPath).startsWith(prefix as LocalPath)
        APath.PathType.SAF -> (this as SAFPath).startsWith(prefix as SAFPath)
        APath.PathType.RAW -> this.path.startsWith(prefix.path)
    }
}

fun APath.removePrefix(prefix: APath, overlap: Int = 0): Segments {
    if (this.pathType != prefix.pathType) {
        throw IllegalArgumentException("removePrefix(): Can't compare different types ($this and $prefix)")
    }
    return when (pathType) {
        APath.PathType.LOCAL -> (this as LocalPath).removePrefixLocalPath(prefix as LocalPath, overlap)
        APath.PathType.SAF -> (this as SAFPath).removePrefixSafPath(prefix as SAFPath, overlap)
        APath.PathType.RAW -> this.segments.drop(prefix.segments.size - overlap)
    }
}

fun Collection<APath>.filterDistinctRoots(): Set<APath> = this
    .sortedBy { it.segments.size }
    .fold<APath, Set<APath>>(emptySet()) { acc, path ->
        if (acc.none { it.isAncestorOf(path) }) {
            acc + path
        } else {
            acc
        }
    }
    .toSet()
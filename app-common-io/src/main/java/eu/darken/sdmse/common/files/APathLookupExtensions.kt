package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import okio.Sink
import okio.Source


val APathLookup<*>.isDirectory: Boolean
    get() = fileType == FileType.DIRECTORY

val APathLookup<*>.isSymlink: Boolean
    get() = fileType == FileType.SYMBOLIC_LINK

val APathLookup<*>.isFile: Boolean
    get() = fileType == FileType.FILE

suspend fun <P : APath, PL : APathLookup<P>, PLE : APathLookupExtended<P>, GT : APathGateway<P, PL, PLE>> PL.walk(
    gateway: GT,
    options: APathGateway.WalkOptions<P, PL> = APathGateway.WalkOptions()
): Flow<PL> = lookedUp.walk(gateway, options)

suspend fun <P : APath, PL : APathLookup<P>> PL.exists(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>
): Boolean = lookedUp.exists(gateway)

suspend fun <P : APath, PL : APathLookup<P>, PLE : APathLookupExtended<P>> PL.delete(
    gateway: APathGateway<P, PL, PLE>
) {
    lookedUp.delete(gateway)
    log(VERBOSE) { "APath.delete(): Deleted $this" }
}

suspend fun <P : APath, PL : APathLookup<P>> PL.deleteAll(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>,
    filter: (APathLookup<*>) -> Boolean = { true }
) = lookedUp.deleteAll(gateway, filter)

suspend fun <P : APath, PL : APathLookup<P>> PL.write(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>
): Sink = lookedUp.write(gateway)

suspend fun <P : APath, PL : APathLookup<P>> PL.read(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>
): Source = lookedUp.read(gateway)

suspend fun <P : APath, PL : APathLookup<P>> PL.canRead(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>
): Boolean = lookedUp.canRead(gateway)

suspend fun <P : APath, PL : APathLookup<P>> PL.canWrite(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>
): Boolean = lookedUp.canWrite(gateway)

suspend fun <P : APath, PL : APathLookup<P>> PL.lookupFiles(
    gateway: APathGateway<P, out APathLookup<P>, out APathLookupExtended<P>>
): Collection<APathLookup<*>> = lookedUp.lookupFiles(gateway)

fun APathLookup<*>.matches(other: APath): Boolean = lookedUp.matches(other)
fun APath.matches(other: APathLookup<*>): Boolean = matches(other.lookedUp)
fun APathLookup<*>.matches(other: APathLookup<*>): Boolean = lookedUp.matches(other.lookedUp)

fun APathLookup<*>.startsWith(prefix: APath): Boolean = lookedUp.startsWith(prefix)
fun APathLookup<*>.startsWith(prefix: APathLookup<*>): Boolean = lookedUp.startsWith(prefix.lookedUp)
fun APath.startsWith(prefix: APathLookup<*>): Boolean = startsWith(prefix.lookedUp)

fun APath.isChildOf(parent: APathLookup<*>): Boolean = isChildOf(parent.lookedUp)
fun APathLookup<*>.isChildOf(parent: APathLookup<*>): Boolean = lookedUp.isChildOf(parent.lookedUp)
fun APathLookup<*>.isChildOf(parent: APath): Boolean = lookedUp.isChildOf(parent)

fun APathLookup<*>.isAncestorOf(descendant: APath): Boolean = lookedUp.isAncestorOf(descendant)
fun APath.isAncestorOf(descendant: APathLookup<*>): Boolean = isAncestorOf(descendant.lookedUp)
fun APathLookup<*>.isAncestorOf(descendant: APathLookup<*>): Boolean = lookedUp.isAncestorOf(descendant.lookedUp)

fun APathLookup<*>.isDescendantOf(ancestor: APath): Boolean = lookedUp.isDescendantOf(ancestor)
fun APath.isDescendantOf(ancestor: APathLookup<*>) = isDescendantOf(ancestor.lookedUp)
fun APathLookup<*>.isDescendantOf(ancestor: APathLookup<*>): Boolean = lookedUp.isDescendantOf(ancestor.lookedUp)

fun APathLookup<*>.isParentOf(child: APath): Boolean = lookedUp.isParentOf(child)
fun APath.isParentOf(child: APathLookup<*>): Boolean = isParentOf(child.lookedUp)
fun APathLookup<*>.isParentOf(child: APathLookup<*>): Boolean = lookedUp.isParentOf(child.lookedUp)

fun APathLookup<*>.removePrefix(prefix: APathLookup<*>, overlap: Int = 0) =
    lookedUp.removePrefix(prefix.lookedUp, overlap)

fun APath.removePrefix(prefix: APathLookup<*>, overlap: Int = 0) =
    this.removePrefix(prefix.lookedUp, overlap)

fun APathLookup<*>.removePrefix(prefix: APath, overlap: Int = 0) =
    lookedUp.removePrefix(prefix, overlap)

fun Collection<APathLookup<*>>.filterDistinctRoots(): Set<APathLookup<*>> {
    val lookupMap = this.associateBy { it.lookedUp }
    return lookupMap.keys.filterDistinctRoots().map { lookupMap.getValue(it) }.toSet()
}
package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup

suspend fun Exclusion.Path.match(candidate: APathLookup<*>): Boolean = match(candidate.lookedUp)

suspend fun PathExclusionIndex.matches(candidate: APathLookup<*>): Boolean = matches(candidate.lookedUp)

fun Exclusion.hasTags(vararg tags: Exclusion.Tag) = this.tags.contains(Exclusion.Tag.GENERAL)
        || tags.any { this.tags.contains(it) }

suspend fun <P : APath, PL : APathLookup<P>> Exclusion.Path.excludeNestedLookups(paths: Collection<PL>): Set<PL> =
    listOf(this).excludeNestedLookups(paths)

suspend fun <P : APath, PL : APathLookup<P>> Collection<Exclusion.Path>.excludeNestedLookups(
    paths: Collection<PL>,
): Set<PL> = PathExclusionIndex(this).excludeNestedLookups(paths)

suspend fun <P : APath, PL : APathLookup<P>> PathExclusionIndex.excludeNestedLookups(
    paths: Collection<PL>,
): Set<PL> {
    val pathMap = paths.associateBy { it.lookedUp }

    val result = this.excludeNested(pathMap.keys)

    return result.map { pathMap[it]!! }.toSet()
}

suspend fun <T : APath> Exclusion.Path.excludeNested(paths: Collection<T>): Set<T> =
    listOf(this).excludeNested(paths)

suspend fun <T : APath> Collection<Exclusion.Path>.excludeNested(paths: Collection<T>): Set<T> =
    PathExclusionIndex(this).excludeNested(paths)

/**
 * Drops every path matched by an exclusion, and then every path that is a strict ancestor of one of
 * those. The second pass exists because deleting a parent directory would take an excluded child
 * with it.
 *
 * This is a single pass over [paths] where the previous implementation folded the whole set once per
 * exclusion. The two agree: whenever a fold step removed a path because it was an ancestor of some
 * excluded `q`, every strict ancestor of that path was also a strict ancestor of `q` and went in the
 * same step, so a later step matching that path directly could never prune anything new.
 */
suspend fun <T : APath> PathExclusionIndex.excludeNested(paths: Collection<T>): Set<T> {
    if (paths.isEmpty()) return emptySet()

    val excluded = mutableListOf<T>()
    val survivors = paths.filter { path ->
        val isExcluded = matches(path)
        if (isExcluded) excluded.add(path)
        !isExcluded
    }
    if (excluded.isEmpty()) return survivors.toSet()

    val ancestorIndex = StrictAncestorIndex(excluded)

    return survivors
        .filterNot { path ->
            val isAncestor = ancestorIndex.isStrictAncestorOfIndexed(path)
            if (isAncestor) log(TAG, VERBOSE) { "Nested exclusion match: $path" }
            isAncestor
        }
        .toSet()
}

private val TAG = logTag("Exclusion", "Extensions")

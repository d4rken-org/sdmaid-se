package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.isAncestorOf

suspend fun Exclusion.Path.match(candidate: APathLookup<*>): Boolean = match(candidate.lookedUp)

fun Exclusion.hasTags(vararg tags: Exclusion.Tag) = this.tags.contains(Exclusion.Tag.GENERAL)
        || tags.any { this.tags.contains(it) }

suspend fun <P : APath, PL : APathLookup<P>> Exclusion.Path.excludeNestedLookups(paths: Collection<PL>): Set<PL> {
    val pathMap = paths.associateBy { it.lookedUp }

    val result = this.excludeNested(pathMap.keys)

    return result.map { pathMap[it]!! }.toSet()
}

suspend fun <P : APath, PL : APathLookup<P>> Collection<Exclusion.Path>.excludeNestedLookups(paths: Collection<PL>): Set<PL> {
    var temp = paths.toSet()
    this.forEach { temp = it.excludeNestedLookups(temp) }
    return temp
}

suspend fun <T : APath> Collection<Exclusion.Path>.excludeNested(paths: Collection<T>): Set<T> {
    var temp = paths.toSet()
    this.forEach { temp = it.excludeNested(temp) }
    return temp
}

suspend fun <T : APath> Exclusion.Path.excludeNested(paths: Collection<T>): Set<T> {
    if (paths.isEmpty()) return emptySet()

    val excluded = mutableSetOf<T>()

    // Files that are left after applying direct exclusions
    val afterFirstPass = paths.filter { path ->
        val isExcluded = match(path)
        if (isExcluded) excluded.add(path)
        !isExcluded
    }

    // Files that are left after also checking ancestors, i.e. if we have:
    // dirA
    // dirA/dirB/
    // dirA/dirB/file
    // and exclude "file", then we also need to exclude all "dirA" and "dirB", otherwise we delete the parent and thus "file"
    val afterSecondPass = afterFirstPass.filterNot { path ->
        val reason = excluded.firstOrNull { path.isAncestorOf(it) }
        if (reason != null) log(TAG, VERBOSE) { "Nested exclusion match: $path <- $reason <- $this" }
        reason != null
    }

    return afterSecondPass.toSet()
}

private val TAG = logTag("Exclusion", "Extensions")
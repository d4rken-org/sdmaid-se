package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.hasTags
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.currentExclusions() = exclusions.first()

suspend fun ExclusionManager.pathExclusions(tool: SDMTool.Type) = currentExclusions()
    .filterIsInstance<Exclusion.Path>()
    .filter {
        return@filter when (tool) {
            SDMTool.Type.CORPSEFINDER -> it.hasTags(Exclusion.Tag.CORPSEFINDER)
            SDMTool.Type.SYSTEMCLEANER -> it.hasTags(Exclusion.Tag.SYSTEMCLEANER)
            SDMTool.Type.APPCLEANER -> it.hasTags(Exclusion.Tag.APPCLEANER)
            SDMTool.Type.APPCONTROL -> throw UnsupportedOperationException()
            SDMTool.Type.ANALYZER -> throw UnsupportedOperationException()
        }
    }

suspend fun ExclusionManager.pkgExclusions(tool: SDMTool.Type) = currentExclusions()
    .filterIsInstance<Exclusion.Pkg>()
    .filter {
        return@filter when (tool) {
            SDMTool.Type.CORPSEFINDER -> it.hasTags(Exclusion.Tag.CORPSEFINDER)
            SDMTool.Type.SYSTEMCLEANER -> it.hasTags(Exclusion.Tag.SYSTEMCLEANER)
            SDMTool.Type.APPCLEANER -> it.hasTags(Exclusion.Tag.APPCLEANER)
            SDMTool.Type.APPCONTROL -> throw UnsupportedOperationException()
            SDMTool.Type.ANALYZER -> throw UnsupportedOperationException()
        }
    }


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
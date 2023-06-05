package eu.darken.sdmse.exclusion.core

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

suspend fun <P : APath, PL : APathLookup<P>> Exclusion.Path.excludeNestedLookups(paths: Collection<PL>): Collection<PL> {
    if (paths.isEmpty()) return emptySet()

    val excluded = mutableSetOf<PL>()

    var temp = paths.filter { path ->
        match(path.lookedUp).also {
            if (it) excluded.add(path)
        }
    }

    temp = temp.filter { path ->
        excluded.none { path.isAncestorOf(it) }
    }

    return temp
}

suspend fun <T : APath> Exclusion.Path.excludeNested(paths: Collection<T>): Collection<T> {
    if (paths.isEmpty()) return emptySet()

    val excluded = mutableSetOf<T>()

    var temp = paths.filter { path ->
        match(path).also {
            if (it) excluded.add(path)
        }
    }

    temp = temp.filter { path ->
        excluded.none { path.isAncestorOf(it) }
    }

    return temp
}
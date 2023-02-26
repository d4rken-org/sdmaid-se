package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.currentExclusions() = exclusions.first()

suspend fun ExclusionManager.pathExclusions(tool: SDMTool.Type) = currentExclusions()
    .filterIsInstance<Exclusion.Path>()
    .filter {
        return@filter if (it.tags.contains(Exclusion.Tag.GENERAL)) true else when (tool) {
            SDMTool.Type.CORPSEFINDER -> it.tags.contains(Exclusion.Tag.CORPSEFINDER)
            SDMTool.Type.SYSTEMCLEANER -> it.tags.contains(Exclusion.Tag.SYSTEMCLEANER)
            SDMTool.Type.APPCLEANER -> it.tags.contains(Exclusion.Tag.APPCLEANER)
            SDMTool.Type.APPCONTROL -> throw UnsupportedOperationException()
        }
    }

suspend fun ExclusionManager.pkgExclusions(tool: SDMTool.Type) = currentExclusions()
    .filterIsInstance<Exclusion.Package>()
    .filter {
        return@filter if (it.tags.contains(Exclusion.Tag.GENERAL)) true else when (tool) {
            SDMTool.Type.CORPSEFINDER -> it.tags.contains(Exclusion.Tag.CORPSEFINDER)
            SDMTool.Type.SYSTEMCLEANER -> it.tags.contains(Exclusion.Tag.SYSTEMCLEANER)
            SDMTool.Type.APPCLEANER -> it.tags.contains(Exclusion.Tag.APPCLEANER)
            SDMTool.Type.APPCONTROL -> throw UnsupportedOperationException()
        }
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
package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.hasTags
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.exists(exclusion: Exclusion) = exists(exclusion.id)

suspend fun ExclusionManager.exists(exclusionId: ExclusionId) = exclusions.first().any { it.id == exclusionId }

suspend fun ExclusionManager.save(exclusion: Exclusion) = save(setOf(exclusion))
suspend fun ExclusionManager.remove(id: ExclusionId) = remove(setOf(id))

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
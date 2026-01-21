package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.hasTags
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.current() = exclusions.first().map { it.exclusion }

suspend fun ExclusionManager.exists(exclusion: Exclusion) = exists(exclusion.id)

suspend fun ExclusionManager.exists(exclusionId: ExclusionId) = current().any { it.id == exclusionId }

suspend fun ExclusionManager.save(exclusion: Exclusion) = save(setOf(exclusion))
suspend fun ExclusionManager.remove(id: ExclusionId) = remove(setOf(id))

suspend fun ExclusionManager.pathExclusions(tool: SDMTool.Type): Collection<Exclusion.Path> = current()
    .filterIsInstance<Exclusion.Path>()
    .filter {
        return@filter when (tool) {
            SDMTool.Type.CORPSEFINDER -> it.hasTags(Exclusion.Tag.CORPSEFINDER)
            SDMTool.Type.SYSTEMCLEANER -> it.hasTags(Exclusion.Tag.SYSTEMCLEANER)
            SDMTool.Type.APPCLEANER -> it.hasTags(Exclusion.Tag.APPCLEANER)
            SDMTool.Type.DEDUPLICATOR -> it.hasTags(Exclusion.Tag.DEDUPLICATOR)
            SDMTool.Type.SWIPER -> it.hasTags(Exclusion.Tag.SWIPER)
            SDMTool.Type.APPCONTROL -> throw UnsupportedOperationException()
            SDMTool.Type.ANALYZER -> throw UnsupportedOperationException()
        }
    }

suspend fun ExclusionManager.pkgExclusions(tool: SDMTool.Type): Collection<Exclusion.Pkg> = current()
    .filterIsInstance<Exclusion.Pkg>()
    .filter {
        return@filter when (tool) {
            SDMTool.Type.CORPSEFINDER -> it.hasTags(Exclusion.Tag.CORPSEFINDER)
            SDMTool.Type.SYSTEMCLEANER -> it.hasTags(Exclusion.Tag.SYSTEMCLEANER)
            SDMTool.Type.APPCLEANER -> it.hasTags(Exclusion.Tag.APPCLEANER)
            SDMTool.Type.DEDUPLICATOR -> throw UnsupportedOperationException()
            SDMTool.Type.SWIPER -> throw UnsupportedOperationException()
            SDMTool.Type.APPCONTROL -> throw UnsupportedOperationException()
            SDMTool.Type.ANALYZER -> throw UnsupportedOperationException()
        }
    }
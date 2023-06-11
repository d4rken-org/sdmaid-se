package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.exists(exclusion: Exclusion) = exists(exclusion.id)

suspend fun ExclusionManager.exists(exclusionId: ExclusionId) = exclusions.first().any { it.id == exclusionId }

suspend fun ExclusionManager.save(exclusion: Exclusion) = save(setOf(exclusion))
suspend fun ExclusionManager.remove(id: ExclusionId) = remove(setOf(id))
package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.exists(exclusion: Exclusion) = exclusions.first().any { it.id == exclusion.id }
package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.core.APathLookup

suspend fun Exclusion.Path.match(candidate: APathLookup<*>): Boolean = match(candidate.lookedUp)
package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.APathLookup

suspend fun Exclusion.Path.match(candidate: APathLookup<*>): Boolean = match(candidate.lookedUp)

fun Exclusion.hasTags(vararg tags: Exclusion.Tag) = this.tags.contains(Exclusion.Tag.GENERAL)
        || tags.any { this.tags.contains(it) }
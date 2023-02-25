package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.files.core.matches
import eu.darken.sdmse.exclusion.core.Exclusion

data class PathExclusion(
    override val path: APath,
    override val tags: Collection<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Path {
    override suspend fun match(candidate: APath): Boolean {
        return candidate.matches(path) || path.isAncestorOf(candidate)
    }
}

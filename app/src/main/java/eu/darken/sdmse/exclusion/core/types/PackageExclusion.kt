package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.exclusion.core.Exclusion

data class PackageExclusion(
    override val pkgId: Pkg.Id,
    override val tags: Collection<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Package {
    override suspend fun match(candidate: Pkg.Id): Boolean {
        return pkgId == candidate
    }
}

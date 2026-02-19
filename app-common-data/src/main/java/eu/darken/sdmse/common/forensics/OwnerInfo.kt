package eu.darken.sdmse.common.forensics

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg

data class OwnerInfo constructor(
    val areaInfo: AreaInfo,
    val owners: Set<Owner>,
    val installedOwners: Set<Owner>,
    val hasUnknownOwner: Boolean,
) {

    val item: APath
        get() = areaInfo.file

    val isKeeper: Boolean
        get() = owners.any { it.hasFlag(Marker.Flag.KEEPER) }

    val isCommon: Boolean
        get() = owners.any { it.hasFlag(Marker.Flag.COMMON) }

    val isOwned: Boolean
        get() = hasUnknownOwner || installedOwners.isNotEmpty()

    val isCorpse: Boolean
        get() = when {
            areaInfo.isBlackListLocation -> {
                // For blacklist locations anything without current owner is a corpse
                installedOwners.isEmpty() && !hasUnknownOwner
            }
            owners.isNotEmpty() -> {
                // For whitelist locations, we need an owner to check against
                installedOwners.isEmpty() && !hasUnknownOwner
            }
            else -> false
        }

    fun getOwner(pkgId: Pkg.Id): Owner? = owners.singleOrNull { it.pkgId == pkgId }

}
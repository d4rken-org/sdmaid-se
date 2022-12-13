package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class OwnerInfo constructor(
    val areaInfo: AreaInfo,
    val owners: Set<Owner>,
    val installedOwners: Set<Owner>,
    val hasUnknownOwner: Boolean,
) : Parcelable {

    val item: APath
        get() = areaInfo.file

    val isKeeper: Boolean
        get() = owners.any { it.hasFlag(Marker.Flag.KEEPER) }

    val isCommon: Boolean
        get() = owners.any { it.hasFlag(Marker.Flag.COMMON) }

    val isCorpse: Boolean
        get() = when {
            areaInfo.isBlackListLocation -> installedOwners.isEmpty() && !hasUnknownOwner
            owners.isNotEmpty() -> installedOwners.isEmpty() && !hasUnknownOwner
            else -> false
        }

    fun getOwner(pkgId: Pkg.Id): Owner? = owners.singleOrNull { it.pkgId == pkgId }

}
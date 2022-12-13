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
    val isCurrentlyOwned: Boolean,
) : Parcelable {

    val item: APath
        get() = areaInfo.file

    val isKeeper: Boolean
        get() {
            for (owner in owners) if (owner.hasFlag(Marker.Flag.KEEPER)) return true
            return false
        }
    val isCommon: Boolean
        get() {
            for (owner in owners) if (owner.hasFlag(Marker.Flag.COMMON)) return true
            return false
        }

    val isCorpse: Boolean
        get() {
            if (areaInfo.isBlackListLocation) return !isCurrentlyOwned else {
                if (!isCurrentlyOwned && owners.isNotEmpty()) return true
            }
            return false
        }

    fun getOwner(pkgId: Pkg.Id): Owner? = owners.singleOrNull { it.pkgId == pkgId }

}
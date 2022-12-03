package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
class OwnerInfo constructor(
    val areaInfo: AreaInfo,
    val owners: Set<Owner>,
    val installedOwners: Set<Owner>,
    val hasUnknownOwner: Boolean = false,
    val isCurrentlyOwned: Boolean? = null,
) : Parcelable {

    val item: APath
        get() = areaInfo.file

//    fun addOwners(matches: Collection<Marker.Match>) {
//        for (match in matches) addOwner(match)
//    }
//
//    fun addOwner(match: Marker.Match) {
//        for (pkg in match.packageNames) owners.add(Owner(pkg, match.flags))
//    }
//
//    fun addOwner(owner: Owner) {
//        owners.add(owner)
//    }

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

//    fun setUnknownOwner(mUnknownOwner: Boolean) {
//        hasUnknownOwner = mUnknownOwner
//    }

//    fun checkOwnerState(fileForensics: FileForensics?): Boolean {
//        for (owner in getOwners()) {
//            if (owner.checkInstalledState(fileForensics)) isCurrentlyOwned = true
//        }
//        if (hasUnknownOwner) isCurrentlyOwned = true
//        if (isCurrentlyOwned == null) isCurrentlyOwned = false
//        return isCurrentlyOwned!!
//    }

    val isCorpse: Boolean
        get() {
            if (areaInfo.isBlackListLocation) return !isCurrentlyOwned() else {
                if (!isCurrentlyOwned() && owners.isNotEmpty()) return true
            }
            return false
        }

    fun isCurrentlyOwned(): Boolean {
        if (isCurrentlyOwned == null) throw RuntimeException("checkOwnerState(...) has not been called!")
        return isCurrentlyOwned
    }

    fun getOwner(pkgId: Pkg.Id): Owner? {
        return owners.singleOrNull { it.pkgId == pkgId }
        for (owner in owners) {
            if (owner.pkgId == pkgId) return owner
        }
        return null
    }

}
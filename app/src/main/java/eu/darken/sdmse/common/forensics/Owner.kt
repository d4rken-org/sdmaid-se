package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class Owner(
    val pkgId: Pkg.Id,
    val flags: Set<Marker.Flag> = emptySet(),
//    val isInstalled: Boolean?,
) : Parcelable {

    fun hasFlag(flag: Marker.Flag): Boolean = flags.contains(flag)
//
//    fun isInstalled(): Boolean {
//        if (isInstalled == null) throw RuntimeException("checkInstalledState(...) has not been called!")
//        return isInstalled
//    }

//    fun checkInstalledState(fileForensics: FileForensics): Boolean {
//        isInstalled = fileForensics.isInstalled(this)
//        return isInstalled!!
//    }
}
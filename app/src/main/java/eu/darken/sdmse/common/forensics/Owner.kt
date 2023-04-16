package eu.darken.sdmse.common.forensics

import android.os.Parcelable
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.parcelize.Parcelize

@Parcelize
data class Owner(
    val pkgId: Pkg.Id,
    val userHandle: UserHandle2,
    val flags: Set<Marker.Flag> = emptySet(),
) : Parcelable {

    val installId: Installed.InstallId
        get() = Installed.InstallId(pkgId, userHandle)

    fun hasFlag(flag: Marker.Flag): Boolean = flags.contains(flag)

}
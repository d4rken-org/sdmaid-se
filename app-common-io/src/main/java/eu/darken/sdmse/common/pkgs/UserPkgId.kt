package eu.darken.sdmse.common.pkgs

import android.os.Parcelable
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserPkgId(
    val pkgId: Pkg.Id,
    val userHandle: UserHandle2,
) : Parcelable

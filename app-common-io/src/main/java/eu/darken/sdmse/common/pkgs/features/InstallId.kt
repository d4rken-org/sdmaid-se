package eu.darken.sdmse.common.pkgs.features

import android.os.Parcelable
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class InstallId(
    val pkgId: Pkg.Id,
    val userHandle: UserHandle2,
) : Parcelable
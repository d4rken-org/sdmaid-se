package eu.darken.sdmse.common.pkgs.features

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.parcelize.Parcelize

interface Installed : PkgInfo {

    val userHandle: UserHandle2

    val installId: InstallId
        get() = InstallId(id, userHandle)

    val sourceDir: APath?
        get() = applicationInfo?.sourceDir?.let { LocalPath.build(it) }

    @Parcelize
    data class InstallId(
        val pkgId: Pkg.Id,
        val userHandle: UserHandle2,
    ) : Parcelable
}
package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.user.UserHandle2

interface Installed : PkgInfo {

    val userHandle: UserHandle2

    val installId: InstallId
        get() = InstallId(id, userHandle)

}
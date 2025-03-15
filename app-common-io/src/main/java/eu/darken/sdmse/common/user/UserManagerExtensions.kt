package eu.darken.sdmse.common.user

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.toPkgId


suspend fun UserManager2.ourInstall() = InstallId(
    pkgId = BuildConfigWrap.APPLICATION_ID.toPkgId(),
    userHandle = currentUser().handle
)
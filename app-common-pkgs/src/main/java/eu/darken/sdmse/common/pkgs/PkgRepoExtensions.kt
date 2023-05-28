package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.flow.first


suspend fun PkgRepo.currentPkgs(): Collection<Installed> = this.pkgs.first()

suspend fun PkgRepo.getPkg(
    pkgId: Pkg.Id,
): Collection<Installed> = query(pkgId, null)

suspend fun PkgRepo.getPkg(
    pkgId: Pkg.Id,
    userHandle: UserHandle2?,
): Installed? = query(pkgId, userHandle).singleOrNull()


suspend fun PkgRepo.getPkg(
    installId: Installed.InstallId
): Installed? = query(installId.pkgId, installId.userHandle).singleOrNull()

suspend fun PkgRepo.isInstalled(
    pkgId: Pkg.Id,
    userHandle: UserHandle2? = null,
): Boolean = query(pkgId, userHandle).isNotEmpty()
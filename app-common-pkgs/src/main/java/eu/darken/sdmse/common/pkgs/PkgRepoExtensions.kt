package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

fun PkgRepo.pkgs(): Flow<Collection<Installed>> = data.map { it.pkgs }

suspend fun PkgRepo.current(): Collection<Installed> = pkgs().first()

suspend fun PkgRepo.get(
    pkgId: Pkg.Id,
): Collection<Installed> = query(pkgId, null)

suspend fun PkgRepo.get(
    pkgId: Pkg.Id,
    userHandle: UserHandle2?,
): Installed? = query(pkgId, userHandle).singleOrNull()

suspend fun PkgRepo.get(
    installId: Installed.InstallId
): Installed? = query(installId.pkgId, installId.userHandle).singleOrNull()

suspend fun PkgRepo.isInstalled(
    pkgId: Pkg.Id,
    userHandle: UserHandle2? = null,
): Boolean = query(pkgId, userHandle).isNotEmpty()
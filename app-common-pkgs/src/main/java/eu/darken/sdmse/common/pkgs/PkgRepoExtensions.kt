package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Android's per-user UID range; a POSIX uid encodes `userId * PER_USER_RANGE + appId`. */
private const val PER_USER_RANGE = 100_000L

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
    installId: InstallId
): Installed? = query(installId.pkgId, installId.userHandle).singleOrNull()

suspend fun PkgRepo.isInstalled(
    pkgId: Pkg.Id,
    userHandle: UserHandle2? = null,
): Boolean = query(pkgId, userHandle).isNotEmpty()

/**
 * Resolves a POSIX owner uid (e.g. from `stat`) to the currently-installed package(s) that hold it.
 *
 * A uid encodes `userId * PER_USER_RANGE + appId`; the appId is shared across users, and multiple
 * packages can share one appId (e.g. the `android.uid.system` appId 1000). Only live [NormalPkg]
 * entries are considered, so archived/uninstalled/hidden cache entries with stale uids can't mask a
 * genuine corpse.
 */
suspend fun PkgRepo.getPkgsForUid(uid: Long): Set<Installed> {
    // uid <= 0 covers root (0) and invalid values; root-owned files have no installed package owner.
    if (uid <= 0) return emptySet()
    val appId = uid % PER_USER_RANGE
    // System uids (< PER_USER_RANGE) decompose to userId 0, which is correct on current Android.
    val userId = (uid / PER_USER_RANGE).toInt()
    return current()
        .filter { it is NormalPkg && it.userHandle.handleId == userId }
        .filter { pkg ->
            // applicationInfo.uid is the appId in the package's own user-space, so compare modulo the range.
            val pkgUid = pkg.applicationInfo?.uid ?: return@filter false
            pkgUid > 0 && pkgUid % PER_USER_RANGE == appId
        }
        .toSet()
}
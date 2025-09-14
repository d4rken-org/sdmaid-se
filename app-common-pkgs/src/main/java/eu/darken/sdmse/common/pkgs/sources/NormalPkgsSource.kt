package eu.darken.sdmse.common.pkgs.sources

import android.content.Context
import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.InvalidPkgInventoryException
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NormalPkgsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pkgOps: PkgOps,
    private val userManager: UserManager2,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
) : PkgDataSource {

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useRes {
        log(TAG, VERBOSE) { "getPkgs()" }

        val pkgs = mutableListOf<Installed>()

        pkgs.addAll(coreList())

        if (rootManager.canUseRootNow() || adbManager.canUseAdbNow()) {
            val extraPkgs = userSpecific()
                .filter { pkg -> !pkgs.any { it.id == pkg.id && it.userHandle == pkg.userHandle } }

            log(TAG) { "${extraPkgs.size} extra pkgs in addition to ${pkgs.size} core list" }
            if (Bugs.isTrace) {
                extraPkgs.forEachIndexed { index, installed -> log(TAG, VERBOSE) { "Extra pkg #$index: $installed" } }
            }

            pkgs.addAll(extraPkgs)
        }

        log(TAG, VERBOSE) { "getPkgs(): Total ${pkgs.size}" }

        pkgs
            .distinctBy { "${it.packageName}:${it.userHandle.handleId}" }
            .also { log(TAG, VERBOSE) { "getPkgs(): Unique ${it.size}" } }
    }

    private suspend fun coreList(): Collection<Installed> {
        log(TAG, VERBOSE) { "coreList()" }

        if (hasApiLevel(34) && !Permission.QUERY_ALL_PACKAGES.isGranted(context)) {
            log(TAG, ERROR) { "QUERY_ALL_PACKAGES is not granted !?" }
            throw QueryAllPkgsPermissionMissing()
        }

        // FYI: MATCH_ALL does not include MATCH_UNINSTALLED_PACKAGES
        val pkgInfos = pkgOps.queryPkgs(PackageManager.MATCH_ALL.toLong())
        if (pkgInfos.isEmpty()) {
            throw InvalidPkgInventoryException("Could not retrieve list of installed packages")
        }
        if (pkgInfos.none { it.packageName == BuildConfigWrap.APPLICATION_ID }) {
            throw InvalidPkgInventoryException("Returned package data didn't contain us")
        }
        if (pkgInfos.none { it.packageName == "android" }) {
            throw InvalidPkgInventoryException("Returned package data didn't contain `android` core package")
        }

        val currentHandle = userManager.currentUser().handle
        val installerData = pkgOps.getInstallerData(pkgInfos)

        val result = pkgInfos.map {
            NormalPkg(
                packageInfo = it,
                userHandle = currentHandle,
                installerInfo = installerData[it] ?: InstallerInfo()
            )
        }

        log(TAG, VERBOSE) { "coreList(): ${result.size} pkgs" }
        if (Bugs.isTrace) {
            result.onEachIndexed { index, installed ->
                log(TAG, VERBOSE) { "coreList(): #$index - $installed" }
            }
        }

        return result
    }

    private suspend fun userSpecific(): Collection<Installed> {
        log(TAG, VERBOSE) { "userSpecific()" }

        val result = userManager.allUsers()
            .map { profile ->
                // FYI: MATCH_ALL does not include MATCH_UNINSTALLED_PACKAGES
                val pkgInfos = pkgOps.queryPkgs(PackageManager.MATCH_ALL.toLong(), profile.handle)
                val installerData = pkgOps.getInstallerData(pkgInfos)

                val userPkgs = pkgInfos.map {
                    NormalPkg(
                        packageInfo = it,
                        userHandle = profile.handle,
                        installerInfo = installerData[it] ?: InstallerInfo()
                    )
                }

                log(TAG, VERBOSE) { "userSpecific(): ${userPkgs.size} pkgs for $profile" }
                if (Bugs.isTrace) {
                    userPkgs.onEachIndexed { index, installed ->
                        log(TAG, VERBOSE) { "userSpecific(): #$index - $installed" }
                    }
                }
                userPkgs
            }
            .flatten()

        return result
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: NormalPkgsSource): PkgDataSource
    }

    companion object {
        private val TAG = logTag("Pkg", "Repo", "Source", "NormalPkgs")
    }
}
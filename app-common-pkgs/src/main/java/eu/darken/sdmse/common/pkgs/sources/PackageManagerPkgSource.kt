package eu.darken.sdmse.common.pkgs.sources

import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.IllegalPkgDataException
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.user.UserManager2
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerPkgSource @Inject constructor(
    private val pkgOps: PkgOps,
    private val userManager: UserManager2,
    private val rootManager: RootManager,
) : PkgDataSource {

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useRes {
        log(TAG, VERBOSE) { "getPkgs()" }

        val pkgs = mutableListOf<Installed>()

        pkgs.addAll(getCoreList())

        if (rootManager.canUseRootNow()) {
            val extraPkgs = getUserSpecificPkgs()
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

    private suspend fun getCoreList(): Collection<Installed> {
        log(TAG, VERBOSE) { "getCoreList()" }

        val result = pkgOps.queryPkgs(PackageManager.MATCH_ALL)

        if (result.isEmpty()) {
            throw IllegalPkgDataException("No installed packages")
        }
        if (result.none { it.packageName == BuildConfigWrap.APPLICATION_ID }) {
            throw IllegalPkgDataException("Returned package data didn't contain us")
        }

        log(TAG, VERBOSE) { "getCoreList(): ${result.size} pkgs" }
        if (Bugs.isTrace) {
            result.onEachIndexed { index, installed ->
                log(TAG, VERBOSE) { "getCoreList(): #$index - $installed" }
            }
        }

        return result
    }

    private suspend fun getUserSpecificPkgs(): Collection<Installed> {
        log(TAG, VERBOSE) { "getUserSpecificPkgs()" }

        val result = userManager.allUsers()
            .map { profile ->
                val userPkgs = pkgOps.queryPkgs(PackageManager.MATCH_ALL, profile.handle)
                log(TAG, VERBOSE) { "getUserSpecificPkgs(): ${userPkgs.size} pkgs for $profile" }
                if (Bugs.isTrace) {
                    userPkgs.onEachIndexed { index, installed ->
                        log(TAG, VERBOSE) { "getUserSpecificPkgs(): #$index - $installed" }
                    }
                }
                userPkgs
            }
            .flatten()

        return result
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PackageManagerPkgSource): PkgDataSource
    }

    companion object {
        private val TAG = logTag("PkgRepo", "Source", "PackageManager")
    }
}
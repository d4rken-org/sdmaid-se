package eu.darken.sdmse.common.pkgs.sources

import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerPkgSource @Inject constructor(
    private val pkgOps: PkgOps,
) : PkgDataSource {

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useSharedResource {
        log(TAG, VERBOSE) { "getPkgs()" }

        val matchAll = pkgOps.getInstalledPackages(PackageManager.MATCH_ALL)
        log(TAG) { "MATCH_ALL yielded ${matchAll.size}" }

        val matchUninstalled = pkgOps.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        log(TAG) { "MATCH_UNINSTALLED_PACKAGES yielded ${matchAll.size}" }
        (matchAll + matchUninstalled).distinctBy { it.packageName }

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PackageManagerPkgSource): PkgDataSource
    }

    companion object {
        private val TAG = logTag("PkgRepo", "Source", "PackageManager")
    }
}
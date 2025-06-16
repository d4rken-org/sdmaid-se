package eu.darken.sdmse.common.pkgs.sources

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.container.UninstalledPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NotInstalledPkgsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pkgOps: PkgOps,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val userManager: UserManager2,
) : PkgDataSource {

    private val targetFlags = MATCH_ARCHIVED_PACKAGES or PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useRes {
        log(TAG) { "getPkgs()" }

        if (hasApiLevel(30) && !Permission.QUERY_ALL_PACKAGES.isGranted(context)) {
            log(TAG, ERROR) { "QUERY_ALL_PACKAGES is not granted !?" }
            throw QueryAllPkgsPermissionMissing()
        }

        val pkgs = mutableListOf<Installed>().apply { addAll(coreList()) }

        if (rootManager.canUseRootNow() || adbManager.canUseAdbNow()) {
            val extraPkgs = userSpecific().filter { pkg ->
                !pkgs.any { it.id == pkg.id && it.userHandle == pkg.userHandle }
            }

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

        return pkgOps.queryPkgs(targetFlags).toPkgs(userManager.currentUser().handle).also {
            log(TAG, VERBOSE) { "coreList(): ${it.size} pkgs" }
            if (Bugs.isTrace) {
                it.onEachIndexed { no, item -> log(TAG, VERBOSE) { "coreList(): #$no - $item" } }
            }
        }
    }

    private suspend fun userSpecific(): Collection<Installed> {
        log(TAG, VERBOSE) { "userSpecific()" }

        return userManager
            .allUsers()
            .map { profile ->
                pkgOps.queryPkgs(targetFlags, profile.handle).toPkgs(profile.handle).also {
                    log(TAG, VERBOSE) { "userSpecific(): ${it.size} pkgs for $profile" }
                    if (Bugs.isTrace) {
                        it.onEachIndexed { no, item -> log(TAG, VERBOSE) { "userSpecific(): #$no - $item" } }
                    }
                }
            }
            .flatten()
    }

    private suspend fun Collection<PackageInfo>.toPkgs(handle: UserHandle2): Collection<Installed> {
        log(TAG, VERBOSE) { "Before conversion: ${this.size} `PackageInfo` items" }
        val installerData = pkgOps.getInstallerData(this)
        val converted = mapNotNull { it.toPkg(handle, installerData[it]) }
        log(TAG, VERBOSE) { "After conversion: ${converted.size} `Installed` items" }
        return converted
    }

    private suspend fun PackageInfo.toPkg(handle: UserHandle2, installerInfo: InstallerInfo?): Installed? = when {
        // Order matters
        isUninstalled() -> UninstalledPkg(
            packageInfo = this,
            userHandle = handle
        ).also { log(TAG, VERBOSE) { "UninstalledPkg: $it" } }

        isArchived() -> ArchivedPkg(
            packageInfo = this,
            userHandle = handle,
            installerInfo = installerInfo ?: InstallerInfo()
        ).also { log(TAG, VERBOSE) { "ArchivedPkg: $it" } }

        isHidden(handle) -> HiddenPkg(
            packageInfo = this,
            userHandle = handle,
        ).also { log(TAG, VERBOSE) { "HiddenPkg: $it" } }

        else -> null
    }

    private val PackageInfo.privateFlags: Int
        get() {
            return try {
                @SuppressLint("DiscouragedPrivateApi")
                val privateFlagsField = applicationInfo!!.javaClass.getDeclaredField("privateFlags").apply {
                    isAccessible = true
                }
                privateFlagsField.getInt(applicationInfo)
            } catch (_: Exception) {
                log(TAG, WARN) { "Failed to get privateFlags for ${this.packageName}" }
                0
            }
        }

    private suspend fun PackageInfo.isArchived(): Boolean = hasApiLevel(35) && applicationInfo?.sourceDir == null

    private suspend fun PackageInfo.isUninstalled(): Boolean = when {
        !hasApiLevel(29) -> {
            val sourceDir = applicationInfo?.sourceDir
            try {
                sourceDir?.let { !File(it).exists() } ?: true
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to check if $sourceDir exists for $packageName:\n${e.asLog()}" }
                false
            }
        }

        else -> (privateFlags and PRIVATE_FLAG_HAS_FRAGILE_USER_DATA) != 0
    }

    private suspend fun PackageInfo.isHidden(handle: UserHandle2): Boolean =
        pkgOps.queryPkg(packageName.toPkgId(), 0L, handle) == null

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: NotInstalledPkgsSource): PkgDataSource
    }

    companion object {
        // TODO Update when using API35: MATCH_ARCHIVED_PACKAGES
        private const val MATCH_ARCHIVED_PACKAGES = 0x100000000L // 4294967296L
        private const val PRIVATE_FLAG_HAS_FRAGILE_USER_DATA = 1 shl 24
        private val TAG = logTag("Pkg", "Repo", "Source", "NotInstalledPkgs")
    }
}
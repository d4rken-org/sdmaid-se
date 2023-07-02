package eu.darken.sdmse.setup

import dagger.Reusable
import eu.darken.sdmse.common.SystemSettingsProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.ourInstall
import javax.inject.Inject

@Reusable
class SetupHelper @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val rootManager: RootManager,
    private val settingsProvider: SystemSettingsProvider,
    private val pkgOps: PkgOps,
    private val userManager2: UserManager2,
) {

    suspend fun checkGrantPermissions(): Boolean {
        if (shizukuManager.canUseShizukuNow()) {
            log(TAG, VERBOSE) { "ensureGrantPermission() available via Shizuku" }
            return true
        }

        if (rootManager.canUseRootNow()) {
            log(TAG, VERBOSE) { "ensureGrantPermission() available via Root" }
            return true
        }

        log(TAG, VERBOSE) { "ensureGrantPermission() is not available" }
        return false
    }

    suspend fun checkSecureSettings(): Boolean {
        if (settingsProvider.hasSecureWriteAccess()) {
            log(TAG, VERBOSE) { "ensureSecureSettings(): We already have secure settings access" }
            return true
        }

        if (!checkGrantPermissions()) {
            log(TAG) { "ensureSecureSettings(): Can't gain grant permissions" }
            return false
        }
        pkgOps.grantPermission(userManager2.ourInstall(), Permission.WRITE_SECURE_SETTINGS)

        return settingsProvider.hasSecureWriteAccess().also {
            if (it) log(TAG, INFO) { "We were able to gain secure settings access :)" }
            else log(TAG, INFO) { "We were not able to gain secure settings access :(" }
        }
    }

    companion object {
        private val TAG = logTag("Setup", "Healer", "Helper")
    }
}
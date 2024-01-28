package eu.darken.sdmse.setup

import dagger.Reusable
import eu.darken.sdmse.common.SystemSettingsProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
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

    suspend fun hasSecureSettings(): Boolean = settingsProvider.hasSecureWriteAccess().also {
        log(TAG, VERBOSE) { "hasSecureSettings(): $it" }
    }

    suspend fun setSecureSettings(granted: Boolean): Boolean {
        log(TAG) { "setSecureSettings(granted=$granted)" }

        if (granted == settingsProvider.hasSecureWriteAccess()) {
            log(TAG, VERBOSE) { "setSecureSettings(granted=$granted): We already have desired access state" }
            return true
        }

        if (!checkGrantPermissions()) {
            log(TAG) { "setSecureSettings(granted=$granted): Can't gain grant permissions" }
            return false
        }

        if (granted) {
            pkgOps.grantPermission(userManager2.ourInstall(), Permission.WRITE_SECURE_SETTINGS)
        } else {
            pkgOps.revokePermission(userManager2.ourInstall(), Permission.WRITE_SECURE_SETTINGS)
        }

        if (granted == settingsProvider.hasSecureWriteAccess()) {
            log(TAG, INFO) { "setSecureSettings(granted=$granted): We achieved desired access state :)" }
        } else {
            log(TAG, ERROR) { "setSecureSettings(granted=$granted): Failed to achieve desired access state :(" }
        }

        return true
    }

    companion object {
        private val TAG = logTag("Setup", "Healer", "Helper")
    }
}
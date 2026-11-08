package eu.darken.sdmse.appcontrol.core.archive

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUpdatedSystemApp
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveSupport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userManager2: UserManager2,
    private val pkgOps: PkgOps,
) {
    /**
     * Checks if app archiving is available and enabled on this device.
     * Requires API 35+ and the archiving feature flag to be enabled.
     *
     * On API 35+, if the Flags class check fails (e.g., due to hidden API restrictions),
     * we assume archiving is available since it's a standard Android 15 feature.
     * If archiving is truly unavailable, the pm archive command will fail gracefully.
     */
    val isArchivingEnabled: Boolean by lazy {
        if (!hasApiLevel(35)) {
            log(TAG, VERBOSE) { "Archiving not available: API level < 35" }
            return@lazy false
        }

        // On API 35+, try to check the feature flag
        // If reflection fails (hidden API restrictions), assume archiving is available
        // since it's a standard Android 15 feature enabled by default
        try {
            @SuppressLint("PrivateApi")
            val flagsClass = Class.forName("android.content.pm.Flags")
            val archivingMethod = flagsClass.getMethod("archiving")
            val result = archivingMethod.invoke(null) as Boolean
            log(TAG, VERBOSE) { "Archiving feature flag: $result" }
            result
        } catch (_: ClassNotFoundException) {
            log(TAG, VERBOSE) { "Flags class not found, assuming archiving available on API 35+" }
            true
        } catch (_: NoSuchMethodException) {
            log(TAG, VERBOSE) { "archiving() method not found, assuming archiving available on API 35+" }
            true
        } catch (e: Exception) {
            log(TAG, VERBOSE) { "Archiving check failed: ${e.message}, assuming available on API 35+" }
            true
        }
    }

    /**
     * Whether Android can archive [installed] and its installer can restore it later.
     * Apps in SD Maid's own user go through `PackageManager.isAppArchivable`, which also requires the installer to
     * have an unarchive receiver. Apps in other users must be in the set [archivablePackages] returns for their
     * user, see [getArchivablePackages].
     */
    @SuppressLint("NewApi")
    suspend fun isArchivable(
        installed: Installed,
        archivablePackages: suspend (UserHandle2) -> Set<String>,
    ): Boolean {
        if (!isArchivingEnabled) return false
        if (installed.isSystemApp || installed.isUpdatedSystemApp) return false

        val installer = (installed as? InstallDetails)?.installerInfo?.installer

        if (installer != null && unsupportedInstallers.contains(installer.id)) {
            log(TAG, VERBOSE) { "Unsupported installer on ${installed.packageName}: $installer" }
            return false
        }

        val user = installed.installId.userHandle
        if (user != userManager2.currentUser().handle) {
            return archivablePackages(user).contains(installed.packageName)
        }

        return try {
            context.packageManager.isAppArchivable(installed.packageName)
        } catch (e: Exception) {
            log(TAG, VERBOSE) { "Failed to check archive support for ${installed.installId}: $e" }
            false
        }
    }

    /**
     * Packages of [user] that Android can archive and whose installer can restore them, looked up via root or ADB.
     * Empty if neither is available or the lookup fails.
     */
    suspend fun getArchivablePackages(user: UserHandle2): Set<String> = try {
        pkgOps.getArchivablePackages(user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "getArchivablePackages($user) failed: $e" }
        emptySet()
    }

    private val unsupportedInstallers = setOf(
        "com.android.shell"
    ).map { it.toPkgId() }

    private val TAG = logTag("AppControl", "ArchiveSupport")
}

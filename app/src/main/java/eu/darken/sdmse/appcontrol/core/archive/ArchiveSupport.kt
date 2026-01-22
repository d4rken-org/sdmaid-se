package eu.darken.sdmse.appcontrol.core.archive

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveSupport @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Checks if app archiving is available and enabled on this device.
     * Requires API 35+ and the archiving feature flag to be enabled.
     *
     * On API 35+, if the Flags class check fails (e.g., due to hidden API restrictions),
     * we assume archiving is available since it's a standard Android 15 feature.
     * If archiving is truly unavailable, the pm archive command will fail gracefully.
     */
    fun isArchivingEnabled(): Boolean {
        if (!hasApiLevel(35)) {
            log(TAG, VERBOSE) { "Archiving not available: API level < 35" }
            return false
        }

        // On API 35+, try to check the feature flag
        // If reflection fails (hidden API restrictions), assume archiving is available
        // since it's a standard Android 15 feature enabled by default
        return try {
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
     * Checks if a specific package can be archived.
     * On API 35+, apps installed from Play Store with Android Gradle Plugin 7.3+ have pre-generated
     * archived APK stubs. getArchivedPackage() returns non-null for these apps.
     */
    @SuppressLint("NewApi")
    fun isArchivable(installed: Installed): Boolean {
        if (!isArchivingEnabled()) return false
        val installer = (installed as? InstallDetails)?.installerInfo?.installer

        if (installer != null && unsupportedInstallers.contains(installer.id)) {
            log(TAG, VERBOSE) { "Unsupported installer on ${installed.packageName}: $installer" }
            return false
        }

        return try {
            context.packageManager.getArchivedPackage(installed.packageName) != null
        } catch (e: Exception) {
            log(TAG, VERBOSE) { "Failed to check archive support for ${installed.installId}: $e" }
            false
        }
    }

    private val unsupportedInstallers = setOf(
        "com.android.shell"
    ).map { it.toPkgId() }

    private val TAG = logTag("AppControl", "ArchiveSupport")
}

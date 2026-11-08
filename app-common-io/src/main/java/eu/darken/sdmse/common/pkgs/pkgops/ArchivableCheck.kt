package eu.darken.sdmse.common.pkgs.pkgops

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

/**
 * The checks AOSP android15-release / android16-release PackageArchiver applies before archiving an app,
 * including the installer check it skips when the caller is the shell UID.
 */
internal object ArchivableCheck {

    fun responsibleInstaller(updateOwner: String?, installingPackage: String?): String? =
        updateOwner?.takeIf { it.isNotEmpty() } ?: installingPackage?.takeIf { it.isNotEmpty() }

    fun isArchivable(
        packageName: String,
        userId: Int,
        installerOf: (packageName: String, userId: Int) -> String?,
        hasUnarchiveReceiver: (installer: String, userId: Int) -> Boolean,
        hasLauncherActivity: (packageName: String, userId: Int) -> Boolean,
        isOptedOut: (packageName: String) -> Boolean,
    ): Boolean = try {
        val installer = installerOf(packageName, userId)
        when {
            installer == null -> false
            !hasUnarchiveReceiver(installer, userId) -> false
            !hasLauncherActivity(packageName, userId) -> false
            isOptedOut(packageName) -> false
            else -> true
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "isArchivable($packageName, $userId) failed: $e" }
        false
    }

    private val TAG = logTag("Pkg", "Ops", "ArchivableCheck")
}

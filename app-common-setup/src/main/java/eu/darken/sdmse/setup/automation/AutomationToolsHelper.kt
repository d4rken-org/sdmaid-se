package eu.darken.sdmse.setup.automation

import android.annotation.SuppressLint
import android.content.Context
import android.security.advancedprotection.AdvancedProtectionManager
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.features.getInstallerInfo
import eu.darken.sdmse.common.pkgs.toPkgId

private val SANCTIONED_SOURCES = setOf(
    "com.android.vending".toPkgId(),
    "org.fdroid.fdroid".toPkgId(),
)
private val FLAGGED_SOURCE_TYPES = setOf(
    InstallerInfo.SourceType.LOCAL_FILE,
    InstallerInfo.SourceType.DOWNLOADED_FILE,
    InstallerInfo.SourceType.UNSPECIFIED,
)

fun Context.mightBeRestrictedDueToSideload(): Boolean {
    if (!hasApiLevel(33)) return false

    val installerInfo = packageManager
        .getPackageInfo(packageName, 0)
        .getInstallerInfo(packageManager)

    val hasGoodSource = installerInfo.allInstallers.any { SANCTIONED_SOURCES.contains(it.id) }
    if (hasGoodSource) return false

    // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/pm/InstallPackageHelper.java;l=2180
    return FLAGGED_SOURCE_TYPES.contains(installerInfo.sourceType)
}

/**
 * Android 17+ "Advanced Protection" blocks any app that is not a dedicated accessibility tool from
 * binding an accessibility service (DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICES, enforced by
 * AccessibilityManagerService). SD Maid is a cleaner, not an accessibility tool, so while Advanced
 * Protection is active our accessibility service can never be enabled - the bind is rejected, not
 * just the Settings toggle, so even a root/ADB (Shizuku) secure-settings write does not help.
 */
@SuppressLint("NewApi", "MissingPermission")
fun Context.isRestrictedByAdvancedProtection(): Boolean {
    if (!hasApiLevel(37)) return false
    return runCatching {
        getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled() == true
    }.getOrDefault(false)
}

data class AcsRestrictionHints(
    val showAdvancedProtectionHint: Boolean,
    val showAppOpsRestrictionHint: Boolean,
)

/**
 * Decides which (mutually exclusive) restriction hint the accessibility setup card should surface.
 *
 * Advanced Protection takes precedence: while it blocks the service the sideload "restricted
 * settings" hint is suppressed, because its remedy (lifting per-app app-ops from the system detail
 * page) cannot lift an Advanced-Protection block.
 */
fun decideAcsRestrictionHints(
    advancedProtectionBlocksAcs: Boolean,
    hasConsent: Boolean?,
    isServiceRunning: Boolean,
    appOpsRestrictionApplies: Boolean,
): AcsRestrictionHints = AcsRestrictionHints(
    showAdvancedProtectionHint = advancedProtectionBlocksAcs && hasConsent == true && !isServiceRunning,
    showAppOpsRestrictionHint = appOpsRestrictionApplies && !advancedProtectionBlocksAcs,
)

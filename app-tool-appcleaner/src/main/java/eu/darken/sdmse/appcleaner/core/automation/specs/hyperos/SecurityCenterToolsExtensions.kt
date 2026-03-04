package eu.darken.sdmse.appcleaner.core.automation.specs.hyperos

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg

/**
 * Checks if the Security Center app is missing the GET_USAGE_STATS permission.
 *
 * @return true if permission is missing, false if granted
 */
internal fun isSecurityCenterMissingPermission(
    context: Context,
    settingsPkgId: Pkg.Id,
    tag: String,
): Boolean = try {
    val pm = context.packageManager
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val targetAppInfo = pm.getApplicationInfo(settingsPkgId.name, 0)

    @Suppress("DEPRECATION")
    val mode = appOps.checkOp(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        targetAppInfo.uid,
        settingsPkgId.name,
    )

    val modeDescription = when (mode) {
        AppOpsManager.MODE_ALLOWED -> "MODE_ALLOWED"
        AppOpsManager.MODE_IGNORED -> "MODE_IGNORED"
        AppOpsManager.MODE_ERRORED -> "MODE_ERRORED"
        AppOpsManager.MODE_DEFAULT -> "MODE_DEFAULT"
        else -> "UNKNOWN($mode)"
    }
    log(tag) { "${settingsPkgId}.GET_USAGE_STATS AppOps mode = $mode ($modeDescription)" }

    val hasPermission = when (mode) {
        AppOpsManager.MODE_ALLOWED -> {
            log(tag) { "Permission explicitly allowed via AppOps" }
            true
        }

        AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> {
            log(tag) { "Permission explicitly denied via AppOps" }
            false
        }

        AppOpsManager.MODE_DEFAULT -> try {
            val result = pm.checkPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
                settingsPkgId.name
            )
            val isGranted = result == PackageManager.PERMISSION_GRANTED
            log(tag) { "MODE_DEFAULT, fallback permission check = $result -> $isGranted" }
            isGranted
        } catch (e: Exception) {
            log(tag, ERROR) { "Failed to check manifest permission: ${e.asLog()}" }
            false
        }

        else -> {
            log(tag) { "Unknown mode $mode, assuming permission denied" }
            false
        }
    }

    log(tag) { "${settingsPkgId}.GET_USAGE_STATS final result: hasPermission = $hasPermission" }

    !hasPermission
} catch (e: Exception) {
    log(tag, ERROR) { "Failed to determine if security center is missing permission: ${e.asLog()}" }
    false
}
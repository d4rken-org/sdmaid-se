package eu.darken.sdmse.appcleaner.core.automation.specs.hyperos

import android.app.AppOpsManager
import android.content.Context
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg

internal fun isSecurityCenterMissingPermission(
    context: Context,
    settingsPkgId: Pkg.Id,
    tag: String,
): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    @Suppress("DEPRECATION")
    val mode = appOps.checkOp(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        context.packageManager.getApplicationInfo(settingsPkgId.name, 0).uid,
        settingsPkgId.name,
    )
    log(tag) { "${settingsPkgId}.GET_USAGE_STATS = $mode" }
    mode != AppOpsManager.MODE_ALLOWED
} catch (e: Exception) {
    log(tag, ERROR) { "Failed to determine if security center is missing permission:${e.asLog()}" }
    false
}
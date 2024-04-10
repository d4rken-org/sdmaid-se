package eu.darken.sdmse.appcontrol.core.automation.specs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

class AppControlLabelDebugger @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationLabelSource {

    suspend fun logAllLabels() {
        log(TAG) { "logAllLabels()" }
        SETTINGS_PKGS
            .filter { context.isInstalled(it.name) }
            .forEach { pkgId ->
                ALL_RES_IDS.forEach { resId ->
                    val label = context.get3rdPartyString(pkgId, resId)
                    log(TAG) { "$pkgId: '$resId' -> '$label'" }
                }
            }
    }

    companion object {
        private val SETTINGS_PKGS = setOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.miui.securitycenter",
        ).map { it.toPkgId() }
        private val RES_IDS_FORCE_STOP = setOf(
            "force_stop",
            "force_stop_dlg_title",
            "okay",
            "dlg_ok",
            "cancel",
            "dlg_cancel",
        )
        private val ALL_RES_IDS = RES_IDS_FORCE_STOP
        private val TAG = logTag("Automation", "ForceStopLabelDebugger")
    }
}
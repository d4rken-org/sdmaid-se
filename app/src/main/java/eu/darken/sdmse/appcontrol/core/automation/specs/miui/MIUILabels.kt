package eu.darken.sdmse.appcontrol.core.automation.specs.miui

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class MIUILabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppControlLabelSource {

    fun getForceStopButtonDynamic(): Set<String> = setOf(
        "force_stop",
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getForceStopDialogTitleDynamic(): Set<String> = setOf(
        "force_stop_dlg_title",
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getForceStopDialogOkDynamic(): Set<String> = setOf(
        "okay",
        "dlg_ok",
    ).getAsStringResources(context, SETTINGS_PKG)

    fun getForceStopDialogCancelDynamic(): Set<String> = setOf(
        "cancel",
        "dlg_cancel",
    ).getAsStringResources(context, SETTINGS_PKG)

    companion object {
        val SETTINGS_PKG = "com.miui.securitycenter".toPkgId()
        val TAG: String = logTag("AppControl", "Automation", "MIUI", "Labels")
    }
}
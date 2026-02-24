package eu.darken.sdmse.appcontrol.core.automation.specs.androidtv

import dagger.Reusable
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
open class AndroidTVLabels @Inject constructor() : AppControlLabelSource {

    fun getForceStopButtonDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("device_apps_app_management_force_stop"))

    fun getForceStopDialogText(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("device_apps_app_management_force_stop_desc"))

    fun getForceStopDialogOkDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("okay", "dlg_ok"))

    fun getForceStopDialogCancelDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("cancel", "dlg_cancel"))

    companion object {
        val SETTINGS_PKG = "com.android.tv.settings".toPkgId()
        val TAG: String = logTag("AppControl", "Automation", "AndroidTV", "Specs")
    }
}

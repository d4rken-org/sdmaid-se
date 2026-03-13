package eu.darken.sdmse.appcontrol.core.automation.specs.aosp

import dagger.Reusable
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class AOSPLabels @Inject constructor() : AppControlLabelSource {

    // Something like "App info"
    fun getSettingsTitleDynamic(
        acsContext: AutomationExplorer.Context,
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("application_info_label"))

    fun getForceStopButtonDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("force_stop"))

    fun getForceStopDialogTitleDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("force_stop_dlg_title"))

    fun getForceStopDialogOkDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("okay", "dlg_ok"))

    fun getForceStopDialogCancelDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("cancel", "dlg_cancel"))

    fun getArchiveButtonDynamic(
        acsContext: AutomationExplorer.Context,
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("archive"))

    fun getRestoreButtonDynamic(
        acsContext: AutomationExplorer.Context,
    ): Set<String> = acsContext.getStrings(SETTINGS_PKG, setOf("restore"))

    companion object {
        val SETTINGS_PKG = "com.android.settings".toPkgId()
        val TAG: String = logTag("AppControl", "Automation", "AOSP", "Labels")
    }
}
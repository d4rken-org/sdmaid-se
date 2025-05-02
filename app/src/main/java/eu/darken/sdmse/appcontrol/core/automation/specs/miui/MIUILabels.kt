package eu.darken.sdmse.appcontrol.core.automation.specs.miui

import dagger.Reusable
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class MIUILabels @Inject constructor(
    private val aospLabels: AOSPLabels,
) : AppControlLabelSource {

    fun getForceStopButtonDynamic(acsContext: AutomationExplorer.Context): Set<String> {
        val labels = mutableSetOf<String>()
        acsContext.getStrings(SETTINGS_PKG, setOf("force_stop")).run {
            labels.addAll(this)
        }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopButtonDynamic(acsContext))
        }
        return labels
    }

    fun getForceStopDialogTitleDynamic(acsContext: AutomationExplorer.Context): Set<String> {
        val labels = mutableSetOf<String>()
        acsContext.getStrings(SETTINGS_PKG, setOf("force_stop_dlg_title")).run {
            labels.addAll(this)
        }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopDialogTitleDynamic(acsContext))
        }
        return labels
    }

    fun getForceStopDialogOkDynamic(acsContext: AutomationExplorer.Context): Set<String> {
        val labels = mutableSetOf<String>()
        acsContext.getStrings(SETTINGS_PKG, setOf("okay", "dlg_ok")).run {
            labels.addAll(this)
        }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopDialogOkDynamic(acsContext))
        }
        return labels
    }

    fun getForceStopDialogCancelDynamic(acsContext: AutomationExplorer.Context): Set<String> {
        val labels = mutableSetOf<String>()
        acsContext.getStrings(SETTINGS_PKG, setOf("cancel", "dlg_cancel")).run {
            labels.addAll(this)
        }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopDialogOkDynamic(acsContext))
        }
        return labels
    }


    companion object {
        val SETTINGS_PKG = "com.miui.securitycenter".toPkgId()
        val TAG: String = logTag("AppControl", "Automation", "MIUI", "Labels")
    }
}
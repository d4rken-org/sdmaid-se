package eu.darken.sdmse.appcontrol.core.automation.specs.samsung

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
open class SamsungLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppControlLabelSource {
    fun getForceStopButtonDynamic(): Set<String> = setOf(
        "force_stop",
    ).getAsStringResources(context, AOSPLabels.SETTINGS_PKG)

    fun getForceStopDialogTitleDynamic(): Set<String> = setOf(
        "force_stop_dlg_title",
    ).getAsStringResources(context, AOSPLabels.SETTINGS_PKG)

    fun getForceStopDialogOkDynamic(): Set<String> = setOf(
        "okay",
        "dlg_ok",
    ).getAsStringResources(context, AOSPLabels.SETTINGS_PKG)

    fun getForceStopDialogCancelDynamic(): Set<String> = setOf(
        "cancel",
        "dlg_cancel",
    ).getAsStringResources(context, AOSPLabels.SETTINGS_PKG)

    companion object {
        val TAG: String = logTag("AppControl", "Automation", "Samsung", "Labels")
    }
}
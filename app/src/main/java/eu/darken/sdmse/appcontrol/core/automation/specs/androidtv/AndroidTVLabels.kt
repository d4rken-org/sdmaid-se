package eu.darken.sdmse.appcontrol.core.automation.specs.androidtv

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject

@Reusable
open class AndroidTVLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppControlLabelSource {

    fun getForceStopButtonDynamic(): Set<String> = setOf(
        "device_apps_app_management_force_stop",
    ).getAsStringResources(context, AndroidTVSpecs.SETTINGS_PKG)

    fun getForceStopDialogText(): Set<String> = setOf(
        "device_apps_app_management_force_stop_desc",
    ).getAsStringResources(context, AndroidTVSpecs.SETTINGS_PKG)

    fun getForceStopDialogOkDynamic(): Set<String> = setOf(
        "okay",
        "dlg_ok",
    ).getAsStringResources(context, AndroidTVSpecs.SETTINGS_PKG)

    fun getForceStopDialogCancelDynamic(): Set<String> = setOf(
        "cancel",
        "dlg_cancel",
    ).getAsStringResources(context, AndroidTVSpecs.SETTINGS_PKG)

    companion object {
        val TAG: String = logTag("AppControl", "Automation", "AndroidTV", "Specs")
    }
}

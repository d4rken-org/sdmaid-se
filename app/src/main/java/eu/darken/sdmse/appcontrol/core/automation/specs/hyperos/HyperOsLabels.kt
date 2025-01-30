package eu.darken.sdmse.appcontrol.core.automation.specs.hyperos

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.automation.specs.AppControlLabelSource
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
class HyperOsLabels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aospLabels: AOSPLabels,
) : AppControlLabelSource {

    fun getForceStopButtonDynamic(): Set<String> {
        val labels = mutableSetOf<String>()
        setOf("force_stop")
            .getAsStringResources(context, SETTINGS_PKG)
            .run { labels.addAll(this) }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopButtonDynamic())
        }
        return labels
    }

    fun getForceStopDialogTitleDynamic(): Set<String> {
        val labels = mutableSetOf<String>()
        setOf("force_stop_dlg_title")
            .getAsStringResources(context, SETTINGS_PKG)
            .run { labels.addAll(this) }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopDialogTitleDynamic())
        }
        return labels
    }

    fun getForceStopDialogOkDynamic(): Set<String> {
        val labels = mutableSetOf<String>()
        setOf("okay", "dlg_ok")
            .getAsStringResources(context, SETTINGS_PKG)
            .run { labels.addAll(this) }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopDialogOkDynamic())
        }
        return labels
    }

    fun getForceStopDialogCancelDynamic(): Set<String> {
        val labels = mutableSetOf<String>()
        setOf("cancel", "dlg_cancel")
            .getAsStringResources(context, SETTINGS_PKG)
            .run { labels.addAll(this) }
        if (labels.isEmpty()) {
            labels.addAll(aospLabels.getForceStopDialogOkDynamic())
        }
        return labels
    }

    companion object {
        val SETTINGS_PKG = "com.miui.securitycenter".toPkgId()
        val TAG: String = logTag("AppControl", "Automation", "HyperOs", "Labels")
    }
}
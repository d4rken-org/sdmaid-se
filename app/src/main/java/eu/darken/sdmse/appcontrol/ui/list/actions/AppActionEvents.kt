package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.Intent
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.export.AppExporter
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.common.pkgs.features.Installed

sealed class AppActionEvents {
    data class SelectExportPath(
        val appInfo: AppInfo,
        val intent: Intent,
    ) : AppActionEvents()

    data class ForceStopResult(
        val result: ForceStopTask.Result,
    ) : AppActionEvents()

    data class ExportResult(
        val successful: Collection<AppExporter.Result>,
        val failed: Collection<Installed.InstallId>,
    ) : AppActionEvents()
}

package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.Intent
import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.appcontrol.core.AppInfo

sealed class AppActionEvents {
    data class SelectExportPath(
        val appInfo: AppInfo,
        val intent: Intent,
    ) : AppActionEvents()

    data class ShowResult(
        val result: AppControlTask.Result,
    ) : AppActionEvents()
}

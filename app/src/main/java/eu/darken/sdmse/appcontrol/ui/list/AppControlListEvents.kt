package eu.darken.sdmse.appcontrol.ui.list

import android.content.Intent
import eu.darken.sdmse.appcontrol.core.export.AppExporter
import eu.darken.sdmse.common.pkgs.features.Installed

sealed class AppControlListEvents {
    data class ConfirmDeletion(val items: List<AppControlListAdapter.Item>) : AppControlListEvents()
    data class ExclusionsCreated(val count: Int) : AppControlListEvents()
    data object ShowSizeSortCaveat : AppControlListEvents()
    data class ConfirmToggle(val items: List<AppControlListAdapter.Item>) : AppControlListEvents()
    data class ExportSelectPath(
        val items: List<AppControlListAdapter.Item>,
        val intent: Intent
    ) : AppControlListEvents()

    data class ExportResult(
        val successful: Collection<AppExporter.Result>,
        val failed: Collection<Installed.InstallId>,
    ) : AppControlListEvents()
}

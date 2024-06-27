package eu.darken.sdmse.appcontrol.core.export

import android.net.Uri
import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppExportTask(
    val targets: Set<Installed.InstallId> = emptySet(),
    val savePath: Uri,
) : AppControlTask, Reportable {

    @Parcelize
    data class Result(
        val success: Set<AppExporter.Result>,
        val failed: Set<Installed.InstallId>,
    ) : AppControlTask.Result, ReportDetails {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
package eu.darken.sdmse.appcontrol.core.export

import android.net.Uri
import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppExportTask(
    val targets: Set<Installed.InstallId> = emptySet(),
    val savePath: Uri,
) : AppControlTask {

    @Parcelize
    data class Result(
        val success: Set<AppExporter.Result>,
        val failed: Set<Installed.InstallId>,
    ) : AppControlTask.Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
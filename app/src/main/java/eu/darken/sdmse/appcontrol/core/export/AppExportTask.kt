package eu.darken.sdmse.appcontrol.core.export

import android.net.Uri
import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.stats.core.AffectedPkg
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
    ) : AppControlTask.Result, ReportDetails.AffectedPkgs {
        override val affectedPkgs: Set<Pkg.Id>
            get() = success.map { it.installId.pkgId }.toSet()

        override val action: AffectedPkg.Action
            get() = AffectedPkg.Action.EXPORTED

        override val primaryInfo: CaString
            get() = caString {
                val succ = getQuantityString2(R.plurals.result_x_successful, success.size)
                val failed = getQuantityString2(R.plurals.result_x_failed, failed.size)
                "$succ | $failed"
            }
    }
}
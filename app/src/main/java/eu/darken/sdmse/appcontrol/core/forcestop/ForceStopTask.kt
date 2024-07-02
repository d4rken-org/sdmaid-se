package eu.darken.sdmse.appcontrol.core.forcestop

import eu.darken.sdmse.appcontrol.core.AppControlTask
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
data class ForceStopTask(
    val targets: Set<Installed.InstallId> = emptySet(),
) : AppControlTask, Reportable {

    @Parcelize
    data class Result(
        val success: Set<Installed.InstallId>,
        val failed: Set<Installed.InstallId>,
    ) : AppControlTask.Result, ReportDetails.AffectedPkgs {

        override val affectedPkgs: Set<Pkg.Id>
            get() = success.map { it.pkgId }.toSet()

        override val action: AffectedPkg.Action
            get() = AffectedPkg.Action.STOPPED

        val isSuccess: Boolean
            get() = success.isNotEmpty() && failed.isEmpty()

        override val primaryInfo: CaString
            get() = caString {
                val succ = getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_successful, success.size)
                val failed = getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_failed, failed.size)
                "$succ | $failed"
            }
    }
}
package eu.darken.sdmse.appcontrol.core.forcestop

import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ForceStopTask(
    val targets: Set<InstallId> = emptySet(),
) : AppControlTask, Reportable {

    @Parcelize
    data class Result(
        val success: Set<InstallId>,
        val failed: Set<InstallId>,
    ) : AppControlTask.Result, ReportDetails.AffectedPkgs {

        override val affectedPkgs: Map<Pkg.Id, AffectedPkg.Action>
            get() = success.associate { it.pkgId to AffectedPkg.Action.STOPPED }

        val isSuccess: Boolean
            get() = success.isNotEmpty() && failed.isEmpty()

        override val primaryInfo: CaString
            get() = caString {
                getQuantityString2(eu.darken.sdmse.R.plurals.appcontrol_force_stop_result_message_x, success.size)
            }

        override val secondaryInfo: CaString?
            get() = failed.takeIf { it.isNotEmpty() }?.let {
                caString {
                    getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_failed, failed.size)
                }
            }
    }
}
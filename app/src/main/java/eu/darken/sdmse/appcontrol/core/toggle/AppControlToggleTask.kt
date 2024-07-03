package eu.darken.sdmse.appcontrol.core.toggle

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
data class AppControlToggleTask(
    val targets: Set<Installed.InstallId> = emptySet(),
) : AppControlTask, Reportable {

    @Parcelize
    data class Result(
        private val enabled: Set<Installed.InstallId>,
        private val disabled: Set<Installed.InstallId>,
        private val failed: Set<Installed.InstallId>,
    ) : AppControlTask.Result, ReportDetails.AffectedPkgs {

        override val affectedPkgs: Map<Pkg.Id, AffectedPkg.Action>
            get() = enabled.associate { it.pkgId to AffectedPkg.Action.ENABLED } + disabled.associate { it.pkgId to AffectedPkg.Action.DISABLED }

        override val primaryInfo: CaString
            get() = caString {
                val succ = getQuantityString2(R.plurals.result_x_successful, enabled.size + disabled.size)
                val failed = getQuantityString2(R.plurals.result_x_failed, failed.size)
                "$succ | $failed"
            }
    }
}
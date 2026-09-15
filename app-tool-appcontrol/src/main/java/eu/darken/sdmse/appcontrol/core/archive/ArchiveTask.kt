package eu.darken.sdmse.appcontrol.core.archive

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
data class ArchiveTask(
    val targets: Set<InstallId> = emptySet(),
) : AppControlTask, Reportable {

    @Parcelize
    data class Result(
        val success: Set<InstallId>,
        val failed: Set<InstallId>,
    ) : AppControlTask.Result, ReportDetails.AffectedPkgs {

        override val affectedPkgs: Map<Pkg.Id, AffectedPkg.Action>
            get() = success.associate { it.pkgId to AffectedPkg.Action.ARCHIVED }

        override val primaryInfo: CaString
            get() = caString {
                val clauses = listOfNotNull(
                    success.size.takeIf { it > 0 }?.let {
                        getQuantityString2(eu.darken.sdmse.appcontrol.R.plurals.appcontrol_archive_result_x, it)
                    },
                    failed.size.takeIf { it > 0 }?.let {
                        getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_failed, it)
                    },
                )
                when {
                    clauses.isEmpty() -> getQuantityString2(
                        eu.darken.sdmse.appcontrol.R.plurals.appcontrol_archive_result_x,
                        success.size,
                    )

                    else -> clauses.joinToString(", ")
                }
            }
    }
}

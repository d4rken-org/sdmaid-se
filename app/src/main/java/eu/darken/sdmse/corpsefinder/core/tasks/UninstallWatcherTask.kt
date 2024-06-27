package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UninstallWatcherTask(
    val target: Pkg.Id,
    val autoDelete: Boolean,
) : CorpseFinderTask, Reportable {

    sealed interface Result : CorpseFinderTask.Result

    @Parcelize
    data class Success(
        private val foundItems: Int,
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
    ) : Result, ReportDetails, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
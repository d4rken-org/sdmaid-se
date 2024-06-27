package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.analyzer.core.AnalyzerTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContentDeleteTask(
    val storageId: StorageId,
    val groupId: ContentGroup.Id,
    val targetPkg: Installed.InstallId? = null,
    val targets: Set<APath>,
) : AnalyzerTask, Reportable {

    @Parcelize
    data class Result(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
    ) : AnalyzerTask.Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
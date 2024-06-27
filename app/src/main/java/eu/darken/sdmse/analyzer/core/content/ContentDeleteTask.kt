package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.analyzer.core.AnalyzerTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.stats.core.HasReportDetails
import eu.darken.sdmse.stats.core.Report
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
        val itemCount: Int,
        val freedSpace: Long,
    ) : AnalyzerTask.Result, HasReportDetails {
        override val reportDetails: Report.Details
            get() = object : Report.Details.SpaceFreed, Report.Details.ItemsProcessed {
                override val spaceFreed: Long = freedSpace

                override val processedCount: Int = itemCount
            }
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
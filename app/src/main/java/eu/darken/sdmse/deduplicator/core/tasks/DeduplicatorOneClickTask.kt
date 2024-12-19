package eu.darken.sdmse.deduplicator.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeduplicatorOneClickTask(
    val noop: Boolean = true,
) : DeduplicatorTask, Reportable {

    sealed interface Result : DeduplicatorTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.deduplicator_result_x_clusters_processed, affectedCount)
            }

        override val secondaryInfo
            get() = caString {
                val (formatted, quantity) = ByteFormatter.formatSize(this, affectedSpace)
                getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.general_result_x_space_freed,
                    quantity,
                    formatted,
                )
            }
    }
}
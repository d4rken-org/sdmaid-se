package eu.darken.sdmse.compressor.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CompressorSchedulerTask(
    val scheduleId: String,
) : CompressorTask, Reportable {

    sealed interface Result : CompressorTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
        val processedCount: Int,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val action: AffectedPath.Action
            get() = AffectedPath.Action.COMPRESSED

        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.compressor_result_x_images_compressed, processedCount)
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

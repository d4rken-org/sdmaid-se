package eu.darken.sdmse.swiper.core.tasks

import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SwiperDeleteTask(
    val sessionId: String,
) : SwiperTask, Reportable {

    sealed interface Result : SwiperTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                getQuantityString2(
                    eu.darken.sdmse.R.plurals.swiper_result_x_items_deleted,
                    affectedCount,
                )
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

    @Parcelize
    data class PartialSuccess(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
        val failedCount: Int,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                getQuantityString2(
                    eu.darken.sdmse.R.plurals.swiper_result_x_items_deleted,
                    affectedCount,
                )
            }

        override val secondaryInfo
            get() = caString {
                getQuantityString2(
                    eu.darken.sdmse.R.plurals.swiper_result_x_items_failed,
                    failedCount,
                    failedCount,
                )
            }
    }
}

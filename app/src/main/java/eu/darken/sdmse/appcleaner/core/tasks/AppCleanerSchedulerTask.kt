package eu.darken.sdmse.appcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.stats.core.HasReportDetails
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppCleanerSchedulerTask(
    val scheduleId: String,
    val useAutomation: Boolean,
) : AppCleanerTask, Reportable {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        private val deletedCount: Int,
        private val recoveredSpace: Long,
    ) : Result, HasReportDetails {
        override val reportDetails: Report.Details
            get() = object : Report.Details.SpaceFreed, Report.Details.ItemsProcessed {
                override val spaceFreed: Long = recoveredSpace

                override val processedCount: Int = deletedCount
            }
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.appcleaner_result_x_items_deleted, deletedCount)
            }

        override val secondaryInfo
            get() = caString {
                getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatFileSize(this, recoveredSpace)
                )
            }
    }
}
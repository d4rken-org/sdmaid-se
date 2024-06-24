package eu.darken.sdmse.appcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.stats.core.HasReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppCleanerOneClickTask(
    val noop: Boolean = true
) : AppCleanerTask, Reportable {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        private val deletedCount: Int,
        private val recoveredSpace: Long,
    ) : Result, HasReportDetails {

        override val primaryInfo: CaString
            get() = caString {
                it.getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatShortFileSize(it, recoveredSpace)
                )
            }
    }
}
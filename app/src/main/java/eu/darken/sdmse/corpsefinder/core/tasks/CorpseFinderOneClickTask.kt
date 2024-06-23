package eu.darken.sdmse.corpsefinder.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.stats.core.HasReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CorpseFinderOneClickTask(
    val noop: Boolean = true,
) : CorpseFinderTask, Reportable {

    sealed interface Result : CorpseFinderTask.Result

    @Parcelize
    data class Success(
        val deletedItems: Int,
        val recoveredSpace: Long,
    ) : Result, HasReportDetails {
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.corpsefinder_result_x_corpses_deleted, deletedItems)
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
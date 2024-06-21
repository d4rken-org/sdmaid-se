package eu.darken.sdmse.corpsefinder.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.stats.core.HasReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CorpseFinderDeleteTask(
    val targetCorpses: Set<CorpseIdentifier>? = null,
    val targetContent: Set<APath>? = null,
) : CorpseFinderTask, Reportable {

    sealed interface Result : CorpseFinderTask.Result

    @Parcelize
    data class Success(
        val deletedItems: Int,
        val recoveredSpace: Long,
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
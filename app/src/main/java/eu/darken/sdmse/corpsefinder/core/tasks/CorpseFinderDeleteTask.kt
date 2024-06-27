package eu.darken.sdmse.corpsefinder.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.corpsefinder.core.CorpseIdentifier
import eu.darken.sdmse.stats.core.ReportDetails
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
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.corpsefinder_result_x_corpses_deleted, affectedCount)
            }

        override val secondaryInfo
            get() = caString {
                getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatFileSize(this, affectedSpace)
                )
            }
    }
}
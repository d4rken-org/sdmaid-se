package eu.darken.sdmse.systemcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import kotlinx.parcelize.Parcelize

@Parcelize
data class SystemCleanerDeleteTask(
    val targetFilters: Set<FilterIdentifier>? = null,
    val targetContent: Set<APath>? = null,
) : SystemCleanerTask {

    sealed interface Result : SystemCleanerTask.Result

    @Parcelize
    data class Success(
        private val deletedItems: Int,
        private val recoveredSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = caString {
                it.getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatShortFileSize(it, recoveredSpace)
                )
            }
    }
}
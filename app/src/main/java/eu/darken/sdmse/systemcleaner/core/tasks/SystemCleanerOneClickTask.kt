package eu.darken.sdmse.systemcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import kotlinx.parcelize.Parcelize

@Parcelize
data class SystemCleanerOneClickTask(
    val noop: Boolean = true,
) : SystemCleanerTask {

    sealed interface Result : SystemCleanerTask.Result

    @Parcelize
    data class Success(
        private val processedItems: Int,
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
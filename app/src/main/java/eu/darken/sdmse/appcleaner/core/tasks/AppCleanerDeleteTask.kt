package eu.darken.sdmse.appcleaner.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppCleanerDeleteTask(
    val toDelete: Set<Pkg.Id> = emptySet(),
) : AppCleanerTask() {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        private val deletedCount: Int,
        private val recoveredSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = caString {
                it.getString(
                    R.string.general_result_x_space_freed,
                    Formatter.formatShortFileSize(it, recoveredSpace)
                )
            }
    }

    @Parcelize
    data class Failure(val error: Exception) : Result {
        override val primaryInfo: CaString
            get() = R.string.general_result_failure_message.toCaString()
    }
}
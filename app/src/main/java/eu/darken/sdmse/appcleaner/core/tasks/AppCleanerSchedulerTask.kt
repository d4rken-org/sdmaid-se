package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppCleanerSchedulerTask(
    val scheduleId: String,
) : AppCleanerTask {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        private val itemCount: Int,
        private val recoverableSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = R.string.general_result_success_message.toCaString()
    }
}
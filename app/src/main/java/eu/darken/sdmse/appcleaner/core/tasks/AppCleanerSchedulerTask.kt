package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppCleanerSchedulerTask(
    val scheduleId: String,
    val useAutomation: Boolean,
) : AppCleanerTask {

    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        private val itemCount: Int,
        private val recoverableSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
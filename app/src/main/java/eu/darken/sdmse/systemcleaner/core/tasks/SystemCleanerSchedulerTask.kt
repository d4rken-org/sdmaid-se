package eu.darken.sdmse.systemcleaner.core.tasks

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.HasReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SystemCleanerSchedulerTask(
    val schedulerId: String,
) : SystemCleanerTask, Reportable {

    sealed interface Result : SystemCleanerTask.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.SYSTEMCLEANER
    }

    @Parcelize
    data class Success(
        private val itemCount: Int,
        private val recoverableSpace: Long,
    ) : Result, HasReportDetails {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
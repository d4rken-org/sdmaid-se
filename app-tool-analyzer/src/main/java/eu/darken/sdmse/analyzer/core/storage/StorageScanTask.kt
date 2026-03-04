package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.AnalyzerTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.storage.StorageId
import kotlinx.parcelize.Parcelize

@Parcelize
data class StorageScanTask(
    val target: StorageId
) : AnalyzerTask {

    @Parcelize
    data class Result(
        private val itemCount: Int,
    ) : AnalyzerTask.Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
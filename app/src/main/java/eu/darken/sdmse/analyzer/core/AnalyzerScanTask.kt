package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import kotlinx.parcelize.Parcelize

@Parcelize
data class AnalyzerScanTask(
    val flag: Boolean = true,
) : AnalyzerTask {

    @Parcelize
    data class Result(
        private val itemCount: Int,
    ) : AnalyzerTask.Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
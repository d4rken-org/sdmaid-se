package eu.darken.sdmse.appcontrol.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppControlScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
) : AppControlTask {

    sealed interface Result : AppControlTask.Result

    @Parcelize
    data class Success(
        private val itemCount: Int,
    ) : Result {
        override val primaryInfo: CaString
            get() = R.string.general_result_success_message.toCaString()
    }

    @Parcelize
    data class Failure(val error: Exception) : Result {
        override val primaryInfo: CaString
            get() = R.string.general_result_failure_message.toCaString()
    }
}
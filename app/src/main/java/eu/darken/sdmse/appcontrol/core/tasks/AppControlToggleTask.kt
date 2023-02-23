package eu.darken.sdmse.appcontrol.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppControlToggleTask(
    val targets: Set<Pkg.Id> = emptySet(),
) : AppControlTask {

    sealed interface Result : AppControlTask.Result

    @Parcelize
    data class Success(
        private val success: Set<Pkg.Id>,
        private val failed: Set<Pkg.Id>,
    ) : Result {
        override val primaryInfo: CaString
            get() = R.string.general_result_success_message.toCaString()
    }
}
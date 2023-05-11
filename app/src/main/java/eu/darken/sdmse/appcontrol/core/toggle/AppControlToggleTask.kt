package eu.darken.sdmse.appcontrol.core.toggle

import eu.darken.sdmse.appcontrol.core.AppControlTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppControlToggleTask(
    val targets: Set<Installed.InstallId> = emptySet(),
) : AppControlTask {

    @Parcelize
    data class Result(
        private val success: Set<Installed.InstallId>,
        private val failed: Set<Installed.InstallId>,
    ) : AppControlTask.Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
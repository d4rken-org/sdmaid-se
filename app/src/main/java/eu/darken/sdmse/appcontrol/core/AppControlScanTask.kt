package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppControlScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
) : AppControlTask {

    @Parcelize
    data class Result(
        private val itemCount: Int,
    ) : AppControlTask.Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
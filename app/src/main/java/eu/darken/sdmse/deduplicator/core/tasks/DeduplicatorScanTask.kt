package eu.darken.sdmse.deduplicator.core.tasks

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeduplicatorScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
) : DeduplicatorTask {

    sealed interface Result : DeduplicatorTask.Result

    @Parcelize
    data class Success(
        private val itemCount: Int,
        private val recoverableSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
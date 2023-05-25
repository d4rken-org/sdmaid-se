package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class UninstallWatcherTask(
    val target: Pkg.Id,
    val autoDelete: Boolean,
) : CorpseFinderTask {

    sealed interface Result : CorpseFinderTask.Result

    @Parcelize
    data class Success(
        private val foundItems: Int,
        private val deletedItems: Int,
        private val recoveredSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = eu.darken.sdmse.common.R.string.general_result_success_message.toCaString()
    }
}
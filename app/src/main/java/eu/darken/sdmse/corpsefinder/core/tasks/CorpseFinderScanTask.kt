package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.pkgs.Pkg
import kotlinx.parcelize.Parcelize

@Parcelize
data class CorpseFinderScanTask(
    val pkgIdFilter: Set<Pkg.Id> = emptySet(),
) : CorpseFinderTask {

    sealed interface Result : CorpseFinderTask.Result

    @Parcelize
    data class Success(
        private val itemCount: Int,
        private val recoverableSpace: Long,
    ) : Result {
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.corpsefinder_result_x_corpses_found, itemCount)
            }

        override val secondaryInfo
            get() = caString {
                val (formatted, quantity) = ByteFormatter.formatSize(this, recoverableSpace)
                getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.x_space_can_be_freed,
                    quantity,
                    formatted,
                )
            }
    }
}
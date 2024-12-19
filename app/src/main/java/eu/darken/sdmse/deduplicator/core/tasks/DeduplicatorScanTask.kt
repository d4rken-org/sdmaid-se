package eu.darken.sdmse.deduplicator.core.tasks

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
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
        private val recoverableSpace: Long,
    ) : Result {
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.deduplicator_result_x_clusters_found, itemCount)
            }

        override val secondaryInfo
            get() = caString {
                val (text, quantity) = ByteFormatter.formatSize(this, recoverableSpace)
                getQuantityString2(
                    R.plurals.deduplicator_x_space_occupied_by_duplicates_msg,
                    quantity,
                    text,
                )
            }
    }
}
package eu.darken.sdmse.deduplicator.core.tasks

import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.deduplicator.core.types.Duplicate
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeduplicatorDeleteTask(
    val targetGroups: Set<Duplicate.Cluster.Identifier>? = null,
    val targetDuplicates: Set<APath>? = null,
) : DeduplicatorTask {

    sealed interface Result : DeduplicatorTask.Result

    @Parcelize
    data class Success(
        val deletedItems: Int,
        val recoveredSpace: Long
    ) : Result {
        override val primaryInfo: CaString
            get() = caString {
                it.getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatShortFileSize(it, recoveredSpace)
                )
            }
    }
}
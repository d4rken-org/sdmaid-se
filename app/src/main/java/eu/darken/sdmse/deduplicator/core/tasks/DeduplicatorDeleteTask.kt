package eu.darken.sdmse.deduplicator.core.tasks

import android.os.Parcelable
import android.text.format.Formatter
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeduplicatorDeleteTask(
    val mode: TargetMode = TargetMode.All,
) : DeduplicatorTask {

    sealed interface TargetMode : Parcelable {

        @Parcelize
        object All : TargetMode

        @Parcelize
        data class Clusters(
            val targets: Set<Duplicate.Cluster.Identifier>,
            val deleteAll: Boolean,
        ) : TargetMode

        @Parcelize
        data class Groups(
            val targets: Set<Duplicate.Group.Identifier>,
            val deleteAll: Boolean,
        ) : TargetMode

        @Parcelize
        data class Duplicates(
            val targets: Set<APath>
        ) : TargetMode
    }

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
package eu.darken.sdmse.deduplicator.core.tasks

import android.os.Parcelable
import android.text.format.Formatter
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class DeduplicatorDeleteTask(
    val mode: TargetMode = TargetMode.All(),
) : DeduplicatorTask, Reportable {

    sealed interface TargetMode : Parcelable {

        @Parcelize
        data class All(
            val id: UUID = UUID.randomUUID(),
        ) : TargetMode

        @Parcelize
        data class Clusters(
            val deleteAll: Boolean = false,
            val targets: Set<Duplicate.Cluster.Id>,
        ) : TargetMode

        @Parcelize
        data class Groups(
            val deleteAll: Boolean = false,
            val targets: Set<Duplicate.Group.Id>,
        ) : TargetMode

        @Parcelize
        data class Duplicates(
            val targets: Set<Duplicate.Id>
        ) : TargetMode
    }

    sealed interface Result : DeduplicatorTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.deduplicator_result_x_clusters_processed, affectedCount)
            }

        override val secondaryInfo
            get() = caString {
                getString(
                    eu.darken.sdmse.common.R.string.general_result_x_space_freed,
                    Formatter.formatFileSize(this, affectedSpace)
                )
            }
    }
}
package eu.darken.sdmse.squeezer.core.tasks

import android.os.Parcelable
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.FailureReason
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class SqueezerProcessTask(
    val mode: TargetMode = TargetMode.All(),
    val qualityOverride: Int? = null,
) : SqueezerTask, Reportable {

    sealed interface TargetMode : Parcelable {

        @Parcelize
        data class All(
            val id: UUID = UUID.randomUUID(),
        ) : TargetMode

        @Parcelize
        data class Selected(
            val targets: Set<CompressibleMedia.Id>,
        ) : TargetMode
    }

    sealed interface Result : SqueezerTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
        val processedCount: Int,
        val failedCount: Int = 0,
        val failureReasons: Map<FailureReason, Int> = emptyMap(),
        /**
         * Photos skipped at process time to preserve their HDR gain map / depth data (the
         * guard re-checks the setting per item). Without this the items just vanish from the
         * pending list, counted neither as processed nor failed.
         */
        val guardSkippedCount: Int = 0,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        /** Files aborted to preserve metadata (part of [failedCount], surfaced as their own clause). */
        val metadataUnpreservableCount: Int
            get() = failureReasons[FailureReason.METADATA_UNPRESERVABLE] ?: 0

        /** [failedCount] minus [metadataUnpreservableCount] so a metadata abort isn't counted twice. */
        val genericFailedCount: Int
            get() = (failedCount - metadataUnpreservableCount).coerceAtLeast(0)

        override val action: AffectedPath.Action
            get() = AffectedPath.Action.COMPRESSED

        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.squeezer_result_x_images_compressed, processedCount)
            }

        override val secondaryInfo
            get() = caString {
                val (formatted, quantity) = ByteFormatter.formatSize(this, affectedSpace)
                val fragments = mutableListOf(
                    getQuantityString2(
                        eu.darken.sdmse.common.R.plurals.general_result_x_space_freed,
                        quantity,
                        formatted,
                    ),
                )
                // Metadata-preservation aborts are part of failedCount; surface them as their own,
                // honest clause and exclude them from the generic "failed" count (see the count
                // properties above) so a single such file isn't shown twice.
                if (genericFailedCount > 0) {
                    fragments += getQuantityString2(R.plurals.squeezer_result_x_items_failed, genericFailedCount)
                }
                if (metadataUnpreservableCount > 0) {
                    fragments += getQuantityString2(
                        R.plurals.squeezer_result_x_metadata_unpreservable,
                        metadataUnpreservableCount,
                    )
                }
                if (guardSkippedCount > 0) {
                    fragments += getQuantityString2(
                        R.plurals.squeezer_result_x_skipped_lossy_aux,
                        guardSkippedCount,
                    )
                }
                fragments.joinToString(" • ")
            }
    }
}

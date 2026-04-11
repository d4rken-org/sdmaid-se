package eu.darken.sdmse.squeezer.core.tasks

import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import kotlinx.parcelize.Parcelize

@Parcelize
data class SqueezerScanTask(
    val paths: Set<APath>? = null,
) : SqueezerTask {

    sealed interface Result : SqueezerTask.Result

    @Parcelize
    data class Success(
        private val itemCount: Int,
        private val totalSize: Long,
        private val estimatedSavings: Long,
        private val skippedInaccessibleCount: Int = 0,
    ) : Result {
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.squeezer_result_x_images_found, itemCount)
            }

        override val secondaryInfo
            get() = caString {
                val (text, quantity) = ByteFormatter.formatSize(this, estimatedSavings)
                val base = getQuantityString2(
                    R.plurals.squeezer_result_x_estimated_savings,
                    quantity,
                    text,
                )
                if (skippedInaccessibleCount > 0) {
                    val skipped = getQuantityString2(
                        R.plurals.squeezer_result_x_skipped_inaccessible,
                        skippedInaccessibleCount,
                        skippedInaccessibleCount,
                    )
                    "$base • $skipped"
                } else {
                    base
                }
            }
    }
}

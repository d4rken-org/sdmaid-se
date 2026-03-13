package eu.darken.sdmse.squeezer.core.tasks

import eu.darken.sdmse.R
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
    ) : Result {
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.squeezer_result_x_images_found, itemCount)
            }

        override val secondaryInfo
            get() = caString {
                val (text, quantity) = ByteFormatter.formatSize(this, estimatedSavings)
                getQuantityString2(
                    R.plurals.squeezer_result_x_estimated_savings,
                    quantity,
                    text,
                )
            }
    }
}

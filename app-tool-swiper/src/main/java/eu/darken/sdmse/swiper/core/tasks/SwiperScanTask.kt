package eu.darken.sdmse.swiper.core.tasks

import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.getQuantityString2
import kotlinx.parcelize.Parcelize

@Parcelize
data class SwiperScanTask(
    val sessionId: String,
) : SwiperTask {

    sealed interface Result : SwiperTask.Result

    @Parcelize
    data class Success(
        val sessionId: String,
        val itemCount: Int,
    ) : Result {
        override val primaryInfo
            get() = caString {
                getQuantityString2(R.plurals.swiper_result_x_items_found, itemCount)
            }
    }
}

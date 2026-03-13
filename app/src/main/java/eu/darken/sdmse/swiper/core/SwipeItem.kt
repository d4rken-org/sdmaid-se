package eu.darken.sdmse.swiper.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup

data class SwipeItem(
    val id: Long,
    val sessionId: String,
    val itemIndex: Int,
    val lookup: APathLookup<APath>,
    val decision: SwipeDecision,
)

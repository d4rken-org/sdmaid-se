package eu.darken.sdmse.swiper.ui

import kotlinx.serialization.Serializable

@Serializable
data class SwiperSwipeRoute(
    val sessionId: String,
    val startIndex: Int = -1,
)

@Serializable
data class SwiperStatusRoute(
    val sessionId: String,
)

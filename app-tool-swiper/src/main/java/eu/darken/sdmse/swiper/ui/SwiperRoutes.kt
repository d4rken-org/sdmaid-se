package eu.darken.sdmse.swiper.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class SwiperSwipeRoute(
    val sessionId: String,
    val startIndex: Int = -1,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<SwiperSwipeRoute>()
    }
}

@Serializable
data class SwiperStatusRoute(
    val sessionId: String,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<SwiperStatusRoute>()
    }
}

package eu.darken.sdmse.swiper.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SwiperSettingsRoute : NavigationDestination

@Serializable
data class SwiperSwipeRoute(
    val sessionId: String,
    val startIndex: Int = -1,
) : NavigationDestination {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<SwiperSwipeRoute>()
    }
}

@Serializable
data class SwiperStatusRoute(
    val sessionId: String,
) : NavigationDestination {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<SwiperStatusRoute>()
    }
}

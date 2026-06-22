package eu.darken.sdmse.swiper.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SwiperSettingsRoute : NavigationDestination

// Routes are bound via the Host entry lambda + ViewModel.bindRoute(); SavedStateHandle.toRoute<>()
// crashes under Nav3 (MissingFieldException), so no from(handle) companions here.
@Serializable
data class SwiperSwipeRoute(
    val sessionId: String,
    val startIndex: Int = -1,
) : NavigationDestination

@Serializable
data class SwiperStatusRoute(
    val sessionId: String,
) : NavigationDestination

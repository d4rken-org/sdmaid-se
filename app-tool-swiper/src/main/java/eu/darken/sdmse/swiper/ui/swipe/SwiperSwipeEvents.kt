package eu.darken.sdmse.swiper.ui.swipe

import android.content.Intent

sealed interface SwiperSwipeEvents {
    data object NavigateToSessions : SwiperSwipeEvents
    data object TriggerHapticFeedback : SwiperSwipeEvents
    data class OpenExternally(val intent: Intent) : SwiperSwipeEvents
    data object ShowOpenNotSupported : SwiperSwipeEvents
}

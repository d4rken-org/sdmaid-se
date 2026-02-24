package eu.darken.sdmse.swiper.ui.swipe

sealed interface SwiperSwipeEvents {
    data object NavigateToSessions : SwiperSwipeEvents
    data object TriggerHapticFeedback : SwiperSwipeEvents
}

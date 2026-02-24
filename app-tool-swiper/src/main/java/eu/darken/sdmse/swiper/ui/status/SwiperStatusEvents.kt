package eu.darken.sdmse.swiper.ui.status

sealed interface SwiperStatusEvents {
    data object NavigateToSessions : SwiperStatusEvents
}

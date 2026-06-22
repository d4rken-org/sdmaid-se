package eu.darken.sdmse.swiper.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.swiper.core.SwiperSettings
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SwiperSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: SwiperSettings,
) : ViewModel4(dispatcherProvider = dispatcherProvider, tag = TAG) {

    val state: StateFlow<State> = combine(
        settings.swapSwipeDirections.flow,
        settings.showFileDetailsOverlay.flow,
        settings.hapticFeedbackEnabled.flow,
    ) { swap, details, haptic ->
        State(
            swapSwipeDirections = swap,
            showFileDetailsOverlay = details,
            hapticFeedbackEnabled = haptic,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setSwapSwipeDirections(value: Boolean) = launch {
        settings.swapSwipeDirections.value(value)
    }

    fun setShowFileDetailsOverlay(value: Boolean) = launch {
        settings.showFileDetailsOverlay.value(value)
    }

    fun setHapticFeedbackEnabled(value: Boolean) = launch {
        settings.hapticFeedbackEnabled.value(value)
    }

    data class State(
        val swapSwipeDirections: Boolean = false,
        val showFileDetailsOverlay: Boolean = true,
        val hapticFeedbackEnabled: Boolean = true,
    )

    companion object {
        private val TAG = logTag("Swiper", "Settings", "ViewModel")
    }
}

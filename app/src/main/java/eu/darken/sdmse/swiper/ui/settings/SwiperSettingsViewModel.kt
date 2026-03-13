package eu.darken.sdmse.swiper.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.swiper.core.SwiperSettings
import javax.inject.Inject

@HiltViewModel
class SwiperSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: SwiperSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    companion object {
        private val TAG = logTag("Swiper", "Settings", "ViewModel")
    }
}

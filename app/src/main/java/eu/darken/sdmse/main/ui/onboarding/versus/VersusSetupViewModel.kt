package eu.darken.sdmse.main.ui.onboarding.versus

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.ui.navigation.OnboardingPrivacyRoute
import javax.inject.Inject

@HiltViewModel
class VersusSetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    fun onContinue() {
        navTo(OnboardingPrivacyRoute)
    }

    companion object {
        private val TAG = logTag("Onboarding", "Versus", "ViewModel")
    }
}

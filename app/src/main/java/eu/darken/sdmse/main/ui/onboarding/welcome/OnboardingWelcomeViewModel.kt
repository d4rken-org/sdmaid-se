package eu.darken.sdmse.main.ui.onboarding.welcome

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.ui.navigation.OnboardingPrivacyRoute
import eu.darken.sdmse.main.ui.navigation.VersusSetupRoute
import javax.inject.Inject

@HiltViewModel
class OnboardingWelcomeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    fun onContinue(isLegacySdmInstalled: Boolean) {
        if (isLegacySdmInstalled) {
            navTo(VersusSetupRoute)
        } else {
            navTo(OnboardingPrivacyRoute)
        }
    }

    companion object {
        private val TAG = logTag("Onboarding", "Welcome", "ViewModel")
    }
}

package eu.darken.sdmse.main.ui.onboarding.setup

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class OnboardingSetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun finishOnboarding() {
        log(TAG) { "finishOnboarding()" }
        generalSettings.isOnboardingCompleted.valueBlocking = true
        OnboardingSetupFragmentDirections.actionOnboardingSetupFragmentToSetupFragment(
            options = SetupScreenOptions(isOnboarding = true)
        ).navigate()
    }

    companion object {
        private val TAG = logTag("Onboarding", "Setup", "ViewModel")
    }
}
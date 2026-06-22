package eu.darken.sdmse.main.ui.onboarding.setup

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.ui.navigation.OnboardingWelcomeRoute
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class OnboardingSetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val navController: NavigationController,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    // Authoritative, synchronous source of truth for the onboarding choice. Seeded from the persisted
    // value (default true on first run) and persisted blocking in finishOnboarding() BEFORE navigation —
    // SetupScreen starts its tour as soon as it renders, so an async write could race and let the tour
    // start with a stale value.
    private val guidedToursEnabled = MutableStateFlow(generalSettings.isGuidedToursEnabled.valueBlocking)

    val state: StateFlow<State> = guidedToursEnabled
        .map { State(isGuidedToursEnabled = it) }
        .safeStateIn(
            initialValue = State(),
            onError = { State() },
        )

    fun onGuidedToursChanged(enabled: Boolean) {
        log(TAG) { "onGuidedToursChanged($enabled)" }
        // Local state only — persisted once (blocking) in finishOnboarding(). Persisting on every
        // toggle would race the blocking finish write and could land a stale value.
        guidedToursEnabled.value = enabled
    }

    fun finishOnboarding() {
        log(TAG) { "finishOnboarding()" }
        generalSettings.isOnboardingCompleted.valueBlocking = true
        // Persist the tour choice blocking so it's durable before SetupScreen can start its tour.
        generalSettings.isGuidedToursEnabled.valueBlocking = guidedToursEnabled.value
        // Clear onboarding back stack, restore Dashboard as root, then push Setup
        navController.goTo(DashboardRoute, popUpTo = OnboardingWelcomeRoute, inclusive = true)
        navController.goTo(SetupRoute(options = SetupScreenOptions(isOnboarding = true)))
    }

    data class State(
        val isGuidedToursEnabled: Boolean = true,
    )

    companion object {
        private val TAG = logTag("Onboarding", "Setup", "ViewModel")
    }
}

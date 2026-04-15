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
import javax.inject.Inject

@HiltViewModel
class OnboardingSetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val navController: NavigationController,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    fun finishOnboarding() {
        log(TAG) { "finishOnboarding()" }
        generalSettings.isOnboardingCompleted.valueBlocking = true
        // Clear onboarding back stack, restore Dashboard as root, then push Setup
        navController.goTo(DashboardRoute, popUpTo = OnboardingWelcomeRoute, inclusive = true)
        navController.goTo(SetupRoute(options = SetupScreenOptions(isOnboarding = true)))
    }

    companion object {
        private val TAG = logTag("Onboarding", "Setup", "ViewModel")
    }
}

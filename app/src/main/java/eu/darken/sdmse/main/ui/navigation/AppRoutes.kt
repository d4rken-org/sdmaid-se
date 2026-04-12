package eu.darken.sdmse.main.ui.navigation

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingWelcomeRoute : NavigationDestination

@Serializable
data object VersusSetupRoute : NavigationDestination

@Serializable
data object OnboardingPrivacyRoute : NavigationDestination

@Serializable
data object OnboardingSetupRoute : NavigationDestination

@Serializable
data object SettingsRoute : NavigationDestination

@Serializable
data object SupportFormRoute : NavigationDestination

@Serializable
data object DebugLogSessionsRoute : NavigationDestination

@Serializable
data object DashboardCardConfigRoute : NavigationDestination

@Serializable
data object GeneralSettingsRoute : NavigationDestination

@Serializable
data object AcknowledgementsRoute : NavigationDestination

@Serializable
data object SupportRoute : NavigationDestination

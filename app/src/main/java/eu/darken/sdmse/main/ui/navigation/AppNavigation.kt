package eu.darken.sdmse.main.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.debug.logviewer.ui.LogViewScreenHost
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.main.ui.dashboard.DashboardScreenHost
import eu.darken.sdmse.common.navigation.routes.DataAreasRoute
import eu.darken.sdmse.common.navigation.routes.LogViewRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.ui.UpgradeScreenHost
import eu.darken.sdmse.main.ui.areas.DataAreasScreenHost
import eu.darken.sdmse.main.ui.onboarding.privacy.OnboardingPrivacyScreenHost
import eu.darken.sdmse.main.ui.onboarding.setup.OnboardingSetupScreenHost
import eu.darken.sdmse.main.ui.onboarding.versus.VersusSetupScreenHost
import eu.darken.sdmse.main.ui.onboarding.welcome.OnboardingWelcomeScreenHost
import eu.darken.sdmse.main.ui.settings.SettingsScreenHost
import eu.darken.sdmse.main.ui.settings.acks.AcknowledgementsScreenHost
import eu.darken.sdmse.main.ui.settings.cards.DashboardCardConfigScreenHost
import eu.darken.sdmse.main.ui.settings.general.GeneralSettingsScreenHost
import eu.darken.sdmse.main.ui.settings.support.SupportScreenHost
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormScreenHost
import eu.darken.sdmse.main.ui.settings.support.sessions.DebugLogSessionsScreenHost
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenHost
import javax.inject.Inject

class AppNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<DashboardRoute> { DashboardScreenHost() }

        entry<OnboardingWelcomeRoute> { OnboardingWelcomeScreenHost() }
        entry<VersusSetupRoute> { VersusSetupScreenHost() }
        entry<OnboardingPrivacyRoute> { OnboardingPrivacyScreenHost() }
        entry<OnboardingSetupRoute> { OnboardingSetupScreenHost() }

        entry<DataAreasRoute> { DataAreasScreenHost() }
        entry<LogViewRoute> { LogViewScreenHost() }
        entry<UpgradeRoute> { UpgradeScreenHost() }
        entry<SetupRoute> { route -> SetupScreenHost(route = route) }

        entry<SettingsRoute> { SettingsScreenHost() }
        entry<GeneralSettingsRoute> { GeneralSettingsScreenHost() }
        entry<AcknowledgementsRoute> { AcknowledgementsScreenHost() }
        entry<SupportRoute> { SupportScreenHost() }
        entry<DashboardCardConfigRoute> { DashboardCardConfigScreenHost() }
        entry<SupportFormRoute> { SupportContactFormScreenHost() }
        entry<DebugLogSessionsRoute> { DebugLogSessionsScreenHost() }
    }
}

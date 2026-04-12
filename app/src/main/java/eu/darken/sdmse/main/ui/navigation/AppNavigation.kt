package eu.darken.sdmse.main.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
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
import javax.inject.Inject

class AppNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderBuilder<NavKey>.setup() {
        entry<DashboardRoute> { DashboardScreenHost() }

        entry<OnboardingWelcomeRoute> { OnboardingWelcomeScreenHost() }
        entry<VersusSetupRoute> { VersusSetupScreenHost() }
        entry<OnboardingPrivacyRoute> { OnboardingPrivacyScreenHost() }
        entry<OnboardingSetupRoute> { OnboardingSetupScreenHost() }

        entry<DataAreasRoute> { DataAreasScreenHost() }
        entry<LogViewRoute> { LogViewScreenHost() }
        entry<UpgradeRoute> { UpgradeScreenHost() }
    }
}

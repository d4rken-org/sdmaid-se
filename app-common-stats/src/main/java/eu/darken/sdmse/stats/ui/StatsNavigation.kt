package eu.darken.sdmse.stats.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.stats.ui.paths.AffectedPathsScreenHost
import eu.darken.sdmse.stats.ui.pkgs.AffectedPkgsScreenHost
import eu.darken.sdmse.stats.ui.reports.ReportsScreenHost
import eu.darken.sdmse.stats.ui.settings.StatsSettingsScreenHost
import eu.darken.sdmse.stats.ui.spacehistory.SpaceHistoryScreenHost
import javax.inject.Inject

class StatsNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<StatsSettingsRoute> { StatsSettingsScreenHost() }
        entry<ReportsRoute> { ReportsScreenHost() }
        entry<AffectedPkgsRoute> { route -> AffectedPkgsScreenHost(route = route) }
        entry<AffectedFilesRoute> { route -> AffectedPathsScreenHost(route = route) }
        entry<SpaceHistoryRoute> { route -> SpaceHistoryScreenHost(route = route) }
    }
}

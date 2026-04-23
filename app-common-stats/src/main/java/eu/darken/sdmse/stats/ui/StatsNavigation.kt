package eu.darken.sdmse.stats.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.stats.ui.settings.StatsSettingsScreenHost
import javax.inject.Inject

class StatsNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<StatsSettingsRoute> { StatsSettingsScreenHost() }
    }
}

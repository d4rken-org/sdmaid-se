package eu.darken.sdmse.deduplicator.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.deduplicator.ui.details.DeduplicatorDetailsScreenHost
import eu.darken.sdmse.deduplicator.ui.list.DeduplicatorListScreenHost
import eu.darken.sdmse.deduplicator.ui.settings.DeduplicatorSettingsScreenHost
import eu.darken.sdmse.deduplicator.ui.settings.arbiter.ArbiterConfigScreenHost
import javax.inject.Inject

class DeduplicatorNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<DeduplicatorSettingsRoute> { DeduplicatorSettingsScreenHost() }
        entry<ArbiterConfigRoute> { ArbiterConfigScreenHost() }
        entry<DeduplicatorListRoute> { DeduplicatorListScreenHost() }
        entry<DeduplicatorDetailsRoute> { route -> DeduplicatorDetailsScreenHost(route = route) }
    }
}

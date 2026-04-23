package eu.darken.sdmse.deduplicator.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.deduplicator.ui.settings.DeduplicatorSettingsScreenHost
import javax.inject.Inject

class DeduplicatorNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<DeduplicatorSettingsRoute> { DeduplicatorSettingsScreenHost() }
    }
}

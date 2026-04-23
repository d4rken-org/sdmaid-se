package eu.darken.sdmse.systemcleaner.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.systemcleaner.ui.settings.SystemCleanerSettingsScreenHost
import javax.inject.Inject

class SystemCleanerNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<SystemCleanerSettingsRoute> { SystemCleanerSettingsScreenHost() }
    }
}

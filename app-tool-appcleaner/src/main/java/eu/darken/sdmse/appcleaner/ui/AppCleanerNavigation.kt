package eu.darken.sdmse.appcleaner.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.appcleaner.ui.settings.AppCleanerSettingsScreenHost
import eu.darken.sdmse.common.navigation.NavigationEntry
import javax.inject.Inject

class AppCleanerNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<AppCleanerSettingsRoute> { AppCleanerSettingsScreenHost() }
    }
}

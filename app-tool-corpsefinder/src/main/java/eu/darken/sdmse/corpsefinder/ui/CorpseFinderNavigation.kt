package eu.darken.sdmse.corpsefinder.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.corpsefinder.ui.details.CorpseDetailsScreenHost
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListScreenHost
import eu.darken.sdmse.corpsefinder.ui.settings.CorpseFinderSettingsScreenHost
import javax.inject.Inject

class CorpseFinderNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<CorpseFinderSettingsRoute> { CorpseFinderSettingsScreenHost() }
        entry<CorpseFinderListRoute> { CorpseFinderListScreenHost() }
        entry<CorpseDetailsRoute> { CorpseDetailsScreenHost() }
    }
}

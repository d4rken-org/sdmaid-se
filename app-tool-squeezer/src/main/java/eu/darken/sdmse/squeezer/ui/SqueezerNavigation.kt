package eu.darken.sdmse.squeezer.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.squeezer.ui.list.SqueezerListScreenHost
import eu.darken.sdmse.squeezer.ui.settings.SqueezerSettingsScreenHost
import eu.darken.sdmse.squeezer.ui.setup.SqueezerSetupScreenHost
import javax.inject.Inject

class SqueezerNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<SqueezerSettingsRoute> { SqueezerSettingsScreenHost() }
        entry<SqueezerSetupRoute> { SqueezerSetupScreenHost() }
        entry<SqueezerListRoute> { SqueezerListScreenHost() }
    }
}

package eu.darken.sdmse.appcontrol.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.appcontrol.ui.list.AppControlListScreenHost
import eu.darken.sdmse.appcontrol.ui.list.actions.AppActionSheetHost
import eu.darken.sdmse.appcontrol.ui.settings.AppControlSettingsScreenHost
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import javax.inject.Inject

class AppControlNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<AppControlSettingsRoute> { AppControlSettingsScreenHost() }
        entry<AppControlListRoute> { AppControlListScreenHost() }
        entry<AppActionRoute> { AppActionSheetHost() }
    }
}

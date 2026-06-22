package eu.darken.sdmse.analyzer.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.analyzer.ui.storage.app.AppDetailsScreenHost
import eu.darken.sdmse.analyzer.ui.storage.apps.AppsScreenHost
import eu.darken.sdmse.analyzer.ui.storage.content.ContentScreenHost
import eu.darken.sdmse.analyzer.ui.storage.device.DeviceStorageScreenHost
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentScreenHost
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import javax.inject.Inject

class AnalyzerNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<DeviceStorageRoute> { DeviceStorageScreenHost() }
        entry<StorageContentRoute> { route -> StorageContentScreenHost(route = route) }
        entry<AppsRoute> { route -> AppsScreenHost(route = route) }
        entry<AppDetailsRoute> { route -> AppDetailsScreenHost(route = route) }
        entry<ContentRoute> { route -> ContentScreenHost(route = route) }
    }
}

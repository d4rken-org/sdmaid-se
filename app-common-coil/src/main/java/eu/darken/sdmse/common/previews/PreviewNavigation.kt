package eu.darken.sdmse.common.previews

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import javax.inject.Inject

class PreviewNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<PreviewRoute> { route -> PreviewScreenHost(route = route) }
    }
}

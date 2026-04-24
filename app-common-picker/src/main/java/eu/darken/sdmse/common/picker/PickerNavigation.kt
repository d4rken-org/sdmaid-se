package eu.darken.sdmse.common.picker

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import javax.inject.Inject

class PickerNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<PickerRoute> { route -> PickerScreenHost(route = route) }
    }
}

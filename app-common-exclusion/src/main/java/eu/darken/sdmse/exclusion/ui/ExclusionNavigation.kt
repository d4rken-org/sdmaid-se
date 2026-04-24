package eu.darken.sdmse.exclusion.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import javax.inject.Inject

class ExclusionNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        // Entries land as each exclusion screen converts from Fragment to Compose.
    }
}

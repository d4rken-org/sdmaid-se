package eu.darken.sdmse.common.navigation

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey

interface NavigationEntry {
    fun EntryProviderBuilder<NavKey>.setup()
}

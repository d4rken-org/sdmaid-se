package eu.darken.sdmse.systemcleaner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SystemCleanerSettingsRoute : NavigationDestination

@Serializable
data object SystemCleanerListRoute : NavigationDestination

@Serializable
data class FilterContentDetailsRoute(
    val filterIdentifier: String? = null,
) : NavigationDestination {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<FilterContentDetailsRoute>()
    }
}

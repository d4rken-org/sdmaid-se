package eu.darken.sdmse.systemcleaner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SystemCleanerSettingsRoute : NavigationDestination

@Serializable
data object SystemCleanerListRoute

@Serializable
data class FilterContentDetailsRoute(
    val filterIdentifier: String? = null,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<FilterContentDetailsRoute>()
    }
}

@Serializable
data class FilterContentRoute(
    val identifier: String,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<FilterContentRoute>()
    }
}

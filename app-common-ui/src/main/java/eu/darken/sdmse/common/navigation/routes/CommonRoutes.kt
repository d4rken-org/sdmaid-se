package eu.darken.sdmse.common.navigation.routes

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute : NavigationDestination

@Serializable
data class UpgradeRoute(
    val forced: Boolean = false,
) : NavigationDestination {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<UpgradeRoute>()
    }
}

@Serializable
data object LogViewRoute : NavigationDestination

@Serializable
data object DataAreasRoute : NavigationDestination

@Serializable
data object AppControlListRoute : NavigationDestination

@Serializable
data object DeviceStorageRoute : NavigationDestination

@Serializable
data object SwiperSessionsRoute : NavigationDestination

@Serializable
data object CustomFilterListRoute : NavigationDestination

package eu.darken.sdmse.common.navigation.routes

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data class UpgradeRoute(
    val forced: Boolean = false,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<UpgradeRoute>()
    }
}

@Serializable
data object LogViewRoute

@Serializable
data object DataAreasRoute

@Serializable
data object AppControlListRoute

@Serializable
data object DeviceStorageRoute

@Serializable
data object SwiperSessionsRoute

@Serializable
data object CustomFilterListRoute

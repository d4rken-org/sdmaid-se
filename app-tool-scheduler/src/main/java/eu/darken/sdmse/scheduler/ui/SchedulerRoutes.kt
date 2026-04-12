package eu.darken.sdmse.scheduler.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SchedulerSettingsRoute : NavigationDestination

@Serializable
data object SchedulerManagerRoute : NavigationDestination

@Serializable
data class ScheduleItemRoute(
    val scheduleId: String,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<ScheduleItemRoute>()
    }
}

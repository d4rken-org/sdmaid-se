package eu.darken.sdmse.scheduler.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data object SchedulerManagerRoute

@Serializable
data class ScheduleItemRoute(
    val scheduleId: String,
) {
    companion object {
        fun from(handle: SavedStateHandle) = handle.toRoute<ScheduleItemRoute>()
    }
}

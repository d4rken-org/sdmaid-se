package eu.darken.sdmse.scheduler.ui

import kotlinx.serialization.Serializable

@Serializable
data object SchedulerManagerRoute

@Serializable
data class ScheduleItemRoute(
    val scheduleId: String,
)

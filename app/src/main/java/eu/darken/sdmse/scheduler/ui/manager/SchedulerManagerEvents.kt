package eu.darken.sdmse.scheduler.ui.manager

import android.content.Intent
import eu.darken.sdmse.scheduler.core.Schedule

sealed interface SchedulerManagerEvents {
    data class FinalCommandsEdit(val schedule: Schedule) : SchedulerManagerEvents

    data class ShowBatteryOptimizationSettings(val intent: Intent) : SchedulerManagerEvents
}
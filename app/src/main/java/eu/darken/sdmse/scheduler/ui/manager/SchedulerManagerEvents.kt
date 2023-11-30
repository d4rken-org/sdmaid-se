package eu.darken.sdmse.scheduler.ui.manager

import eu.darken.sdmse.scheduler.core.Schedule

sealed interface SchedulerManagerEvents {
    data class FinalCommandsEdit(val schedule: Schedule) : SchedulerManagerEvents
}
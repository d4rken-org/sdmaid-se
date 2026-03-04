package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool

fun interface SchedulerTaskFactory {
    suspend fun createTasks(
        scheduleId: String,
        enabledTools: Set<SDMTool.Type>,
    ): List<SDMTool.Task>
}

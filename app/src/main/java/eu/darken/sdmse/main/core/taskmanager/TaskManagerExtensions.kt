package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool

fun TaskManager.State.getLatestTask(tool: SDMTool.Type): TaskManager.ManagedTask? {
    return tasks.filter { it.toolType == tool && it.isComplete }.maxByOrNull { it.completedAt!! }
}

fun TaskManager.State.getLatestResult(tool: SDMTool.Type): SDMTool.Task.Result? {
    return getLatestTask(tool)?.result
}

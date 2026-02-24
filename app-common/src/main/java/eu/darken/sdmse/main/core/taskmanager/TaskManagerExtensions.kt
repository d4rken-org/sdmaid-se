package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool

fun TaskSubmitter.State.getLatestTask(tool: SDMTool.Type): TaskSubmitter.ManagedTask? {
    return tasks.filter { it.toolType == tool && it.isComplete }.maxByOrNull { it.completedAt!! }
}

fun TaskSubmitter.State.getLatestResult(tool: SDMTool.Type): SDMTool.Task.Result? {
    return getLatestTask(tool)?.result
}

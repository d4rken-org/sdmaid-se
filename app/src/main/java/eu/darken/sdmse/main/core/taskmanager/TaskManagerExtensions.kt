package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool

fun TaskManager.State.getLatestResult(tool: SDMTool.Type): SDMTool.Task.Result? {
    return tasks.filter { it.toolType == tool && it.isComplete }.maxByOrNull { it.completedAt!! }?.result
}
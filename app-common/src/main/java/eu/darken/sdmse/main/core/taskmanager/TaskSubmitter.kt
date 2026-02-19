package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool

interface TaskSubmitter {
    suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result
}

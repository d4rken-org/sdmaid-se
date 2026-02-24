package eu.darken.sdmse.automation.core

import kotlinx.coroutines.flow.Flow

interface AutomationSubmitter {
    val useAcs: Flow<Boolean>
    suspend fun submit(task: AutomationTask): AutomationTask.Result
}

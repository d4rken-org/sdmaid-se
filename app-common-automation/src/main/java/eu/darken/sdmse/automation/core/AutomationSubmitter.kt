package eu.darken.sdmse.automation.core

interface AutomationSubmitter {
    suspend fun submit(task: AutomationTask): AutomationTask.Result
}

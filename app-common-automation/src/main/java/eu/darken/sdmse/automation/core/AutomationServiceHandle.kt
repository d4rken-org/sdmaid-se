package eu.darken.sdmse.automation.core

interface AutomationServiceHandle {
    suspend fun submit(task: AutomationTask): AutomationTask.Result
    fun cancelTask(): Boolean
    fun disableSelf()
}

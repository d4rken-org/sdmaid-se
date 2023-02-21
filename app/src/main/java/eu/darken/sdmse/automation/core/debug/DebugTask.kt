package eu.darken.sdmse.automation.core.debug

import eu.darken.sdmse.automation.core.AutomationTask

class DebugTask : AutomationTask {

    data class Result(val task: DebugTask) : AutomationTask.Result
}
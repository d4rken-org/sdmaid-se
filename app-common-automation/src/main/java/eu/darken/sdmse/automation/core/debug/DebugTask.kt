package eu.darken.sdmse.automation.core.debug

import eu.darken.sdmse.automation.core.AutomationTask
import java.util.UUID

data class DebugTask(
    val runId: UUID = UUID.randomUUID(),
) : AutomationTask {

    data class Result(val task: DebugTask) : AutomationTask.Result
}
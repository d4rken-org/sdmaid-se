package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError
import eu.darken.sdmse.main.core.SDMTool
import kotlin.time.Duration

class TaskTimeoutException(
    val toolType: SDMTool.Type,
    val timeout: Duration,
) : Exception("Task for $toolType timed out after $timeout"), HasLocalizedError {

    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.tasks_timeout_error_label.toCaString(),
        description = R.string.tasks_timeout_error_desc.toCaString(),
    )
}

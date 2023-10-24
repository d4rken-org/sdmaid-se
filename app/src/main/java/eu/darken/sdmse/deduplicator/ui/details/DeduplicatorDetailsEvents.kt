package eu.darken.sdmse.deduplicator.ui.details

import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask

sealed class DeduplicatorDetailsEvents {
    data class TaskResult(val result: DeduplicatorTask.Result) : DeduplicatorDetailsEvents()
}

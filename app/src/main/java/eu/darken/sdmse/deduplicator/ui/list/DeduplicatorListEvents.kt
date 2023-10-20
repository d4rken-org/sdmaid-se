package eu.darken.sdmse.deduplicator.ui.list

import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask

sealed class DeduplicatorListEvents {
    data class ConfirmDeletion(val items: Collection<DeduplicatorListAdapter.Item>) : DeduplicatorListEvents()
    data class TaskResult(val result: DeduplicatorTask.Result) : DeduplicatorListEvents()
    data class ExclusionsCreated(val count: Int) : DeduplicatorListEvents()
}

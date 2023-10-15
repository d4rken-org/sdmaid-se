package eu.darken.sdmse.deduplicator.ui.list

import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask

sealed class DuplicateGroupListEvents {
    data class ConfirmDeletion(val items: Collection<DuplicateGroupListAdapter.Item>) : DuplicateGroupListEvents()
    data class TaskResult(val result: DeduplicatorTask.Result) : DuplicateGroupListEvents()
    data class ExclusionsCreated(val count: Int) : DuplicateGroupListEvents()
}

package eu.darken.sdmse.corpsefinder.ui.list

import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask

sealed class CorpseListEvents {
    data class ConfirmDeletion(val items: Collection<CorpseListAdapter.Item>) : CorpseListEvents()
    data class TaskResult(val result: CorpseFinderTask.Result) : CorpseListEvents()
    data class ExclusionsCreated(val count: Int) : CorpseListEvents()
}

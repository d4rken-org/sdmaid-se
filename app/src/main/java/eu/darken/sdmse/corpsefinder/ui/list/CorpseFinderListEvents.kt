package eu.darken.sdmse.corpsefinder.ui.list

import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask

sealed class CorpseFinderListEvents {
    data class ConfirmDeletion(val items: Collection<CorpseFinderListAdapter.Item>) : CorpseFinderListEvents()
    data class TaskResult(val result: CorpseFinderTask.Result) : CorpseFinderListEvents()
    data class ExclusionsCreated(val count: Int) : CorpseFinderListEvents()
}

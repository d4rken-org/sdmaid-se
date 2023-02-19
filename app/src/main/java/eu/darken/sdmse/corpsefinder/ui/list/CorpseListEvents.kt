package eu.darken.sdmse.corpsefinder.ui.list

import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask

sealed class CorpseListEvents {
    data class ConfirmDeletion(val corpse: Corpse) : CorpseListEvents()
    data class TaskResult(val result: CorpseFinderTask.Result) : CorpseListEvents()
}

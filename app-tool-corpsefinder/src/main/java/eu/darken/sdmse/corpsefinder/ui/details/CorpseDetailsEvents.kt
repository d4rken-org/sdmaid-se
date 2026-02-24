package eu.darken.sdmse.corpsefinder.ui.details

import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask

sealed class CorpseDetailsEvents {
    data class TaskResult(val result: CorpseFinderTask.Result) : CorpseDetailsEvents()
}

package eu.darken.sdmse.corpsefinder.ui.details.corpse

import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask

sealed class CorpseEvents {
    data class ConfirmDeletion(val items: Collection<CorpseElementsAdapter.Item>) : CorpseEvents()
    data class TaskForParent(val task: CorpseFinderTask) : CorpseEvents()
}

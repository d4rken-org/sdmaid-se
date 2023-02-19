package eu.darken.sdmse.corpsefinder.ui.details.corpse

import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask

sealed class CorpseEvents {
    data class ConfirmDeletion(val corpse: Corpse) : CorpseEvents()
    data class TaskForParent(val task: CorpseFinderTask) : CorpseEvents()
}

package eu.darken.sdmse.corpsefinder.ui.list

import eu.darken.sdmse.corpsefinder.core.Corpse

sealed class CorpseListEvents {
    data class ConfirmDeletion(
        val corpse: Corpse
    ) : CorpseListEvents()
}

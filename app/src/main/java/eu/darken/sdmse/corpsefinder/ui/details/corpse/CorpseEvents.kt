package eu.darken.sdmse.corpsefinder.ui.details.corpse

import eu.darken.sdmse.corpsefinder.core.Corpse

sealed class CorpseEvents {
    data class ConfirmDeletion(
        val corpse: Corpse,
    ) : CorpseEvents()
}

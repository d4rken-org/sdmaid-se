package eu.darken.sdmse.corpsefinder.ui.details.corpse

sealed class CorpseEvents {
    data class ConfirmDeletion(val items: Collection<CorpseElementsAdapter.Item>) : CorpseEvents()
}

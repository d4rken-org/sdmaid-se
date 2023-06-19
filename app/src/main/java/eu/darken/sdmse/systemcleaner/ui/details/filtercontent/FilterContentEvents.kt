package eu.darken.sdmse.systemcleaner.ui.details.filtercontent

sealed class FilterContentEvents {
    data class ConfirmDeletion(val items: Collection<FilterContentElementsAdapter.Item>) : FilterContentEvents()
}

package eu.darken.sdmse.appcontrol.ui.list

sealed class AppControlListEvents {
    data class ConfirmDeletion(val items: List<AppControlListAdapter.Item>) : AppControlListEvents()
    data class ExclusionsCreated(val count: Int) : AppControlListEvents()
    data object ShowSizeSortCaveat : AppControlListEvents()
    data class ConfirmToggle(val items: List<AppControlListAdapter.Item>) : AppControlListEvents()
}

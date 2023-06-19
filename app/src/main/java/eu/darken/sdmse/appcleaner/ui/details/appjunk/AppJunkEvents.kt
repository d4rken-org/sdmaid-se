package eu.darken.sdmse.appcleaner.ui.details.appjunk

sealed class AppJunkEvents {
    data class ConfirmDeletion(val items: Collection<AppJunkElementsAdapter.Item>) : AppJunkEvents()
}

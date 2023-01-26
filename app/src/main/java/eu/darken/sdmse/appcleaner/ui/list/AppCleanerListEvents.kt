package eu.darken.sdmse.appcleaner.ui.list

import eu.darken.sdmse.appcleaner.core.AppJunk

sealed class AppCleanerListEvents {
    data class ConfirmDeletion(
        val appJunk: AppJunk
    ) : AppCleanerListEvents()
}

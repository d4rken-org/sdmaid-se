package eu.darken.sdmse.appcontrol.ui.list

import eu.darken.sdmse.appcleaner.core.AppJunk

sealed class AppControlListEvents {
    data class ConfirmDeletion(val appJunk: AppJunk) : AppControlListEvents()
    data class ExclusionsCreated(val count: Int) : AppControlListEvents()
}

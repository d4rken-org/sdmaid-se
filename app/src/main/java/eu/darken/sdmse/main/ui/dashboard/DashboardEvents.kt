package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.systemcleaner.core.FilterContent

sealed interface DashboardEvents {

    object SetupDismissHint : DashboardEvents

    data class CorpseFinderDeleteAllConfirmation(
        val corpses: Collection<Corpse>,
    ) : DashboardEvents

    data class SystemCleanerDeleteAllConfirmation(
        val sieves: Collection<FilterContent>,
    ) : DashboardEvents

    data class AppCleanerDeleteAllConfirmation(
        val appJunks: Collection<AppJunk>,
    ) : DashboardEvents
}
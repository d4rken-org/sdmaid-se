package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.systemcleaner.core.SieveContent

sealed interface DashboardEvents {
    data class CorpseFinderDeleteAllConfirmation(
        val corpses: Collection<Corpse>,
    ) : DashboardEvents

    data class SystemCleanerDeleteAllConfirmation(
        val sieves: Collection<SieveContent>,
    ) : DashboardEvents

    data class AppCleanerDeleteAllConfirmation(
        val appJunks: Collection<AppJunk>,
    ) : DashboardEvents
}
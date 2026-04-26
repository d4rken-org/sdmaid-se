package eu.darken.sdmse.scheduler.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.scheduler.ui.manager.SchedulerManagerScreenHost
import eu.darken.sdmse.scheduler.ui.manager.create.ScheduleItemSheetHost
import eu.darken.sdmse.scheduler.ui.settings.SchedulerSettingsScreenHost
import javax.inject.Inject

class SchedulerNavigation @Inject constructor() : NavigationEntry {

    override fun EntryProviderScope<NavKey>.setup() {
        entry<SchedulerSettingsRoute> { SchedulerSettingsScreenHost() }
        entry<SchedulerManagerRoute> { SchedulerManagerScreenHost() }
        entry<ScheduleItemRoute> { route -> ScheduleItemSheetHost(scheduleId = route.scheduleId) }
    }
}

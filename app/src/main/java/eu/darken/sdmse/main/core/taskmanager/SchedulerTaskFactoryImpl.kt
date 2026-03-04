package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerTaskFactoryImpl @Inject constructor(
    private val schedulerSettings: SchedulerSettings,
) : SchedulerTaskFactory {

    override suspend fun createTasks(
        scheduleId: String,
        enabledTools: Set<SDMTool.Type>,
    ): List<SDMTool.Task> = enabledTools.mapNotNull { type ->
        when (type) {
            SDMTool.Type.CORPSEFINDER -> CorpseFinderSchedulerTask(scheduleId)
            SDMTool.Type.SYSTEMCLEANER -> SystemCleanerSchedulerTask(scheduleId)
            SDMTool.Type.APPCLEANER -> {
                val useAutomation = schedulerSettings.useAutomation.value()
                AppCleanerSchedulerTask(scheduleId, useAutomation = useAutomation)
            }
            else -> null
        }
    }
}

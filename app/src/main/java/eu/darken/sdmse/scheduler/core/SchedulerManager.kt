package eu.darken.sdmse.scheduler.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val settings: SchedulerSettings,
    private val storage: ScheduleStorage
) {
    private val internalState = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        State(
            schedules = storage.load()
        )
    }

    data class State(
        val schedules: Collection<Schedule>,
    )

    val state: Flow<State> = internalState.flow

    suspend fun getSchedule(scheduleId: String): Schedule? {
        return state.first().schedules.singleOrNull { it.id == scheduleId }
    }

    suspend fun saveSchedule(schedule: Schedule) = withContext(NonCancellable) {
        log(TAG) { "saveSchedule(): $schedule" }
        val newSchedules = storage.load().filter { it.id != schedule.id }.plus(schedule).toSet()
        storage.save(newSchedules)
        internalState.updateBlocking {
            copy(schedules = newSchedules)
        }
    }

    suspend fun removeSchedule(scheduleId: String) {
        log(TAG) { "removeSchedule(): $scheduleId" }
        val newSchedules = storage.load().filter { it.id != scheduleId }.toSet()
        storage.save(newSchedules)
        internalState.updateBlocking {
            copy(schedules = newSchedules)
        }
    }

    companion object {
        internal val TAG = logTag("Scheduler", "Manager")
    }
}
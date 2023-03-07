package eu.darken.sdmse.scheduler.ui.manager.item

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import kotlinx.coroutines.flow.flow
import javax.inject.Inject


@HiltViewModel
class ScheduleItemDialogVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val schedulerManager: SchedulerManager,
) : ViewModel3(dispatcherProvider) {
    private val navArgs by handle.navArgs<ScheduleItemDialogArgs>()

    private val scheduleId: String = navArgs.scheduleId

    val state = flow {
        var schedule = schedulerManager.getSchedule(scheduleId)
        var isNew = false
        if (schedule == null) {
            isNew = true
            schedule = Schedule(id = scheduleId)
        }
        emit(
            State(
                schedule = schedule,
                isNew = isNew
            )
        )
    }
        .asLiveData2()

    fun saveSchedule(
        label: String,
        corpseFinder: Boolean,
        systemCleaner: Boolean,
        appCleaner: Boolean
    ) = launch {
        log(TAG) { "saveSchedule()" }
        val newSchedule = state.value!!.schedule.copy(
            label = label,
            useCorpseFinder = corpseFinder,
            useSystemCleaner = systemCleaner,
            useAppCleaner = appCleaner
        )
        schedulerManager.saveSchedule(newSchedule)
        popNavStack()
    }

    fun deleteSchedule() = launch {
        log(TAG) { "deleteSchedule()" }
        schedulerManager.removeSchedule(scheduleId)
        popNavStack()
    }

    data class State(
        val schedule: Schedule,
        var isNew: Boolean,
    )

    companion object {
        private val TAG = logTag("Scheduler", "Schedule", "Dialog", "VM")
    }

}
package eu.darken.sdmse.scheduler.ui.manager.create

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import java.time.Duration
import javax.inject.Inject


@HiltViewModel
class ScheduleItemViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val schedulerManager: SchedulerManager,
) : ViewModel3(dispatcherProvider) {
    private val navArgs by handle.navArgs<ScheduleItemDialogArgs>()

    private val scheduleId: String = navArgs.scheduleId

    private val internalState = DynamicStateFlow<State>(parentScope = vmScope) {
        val existing = schedulerManager.getSchedule(scheduleId)
        State(
            existing = existing,
            label = existing?.label,
            hour = existing?.hour,
            minute = existing?.minute,
            repeatInterval = existing?.repeatInterval ?: Duration.ofDays(3),
        )
    }
    val state = internalState.flow.asLiveData2()

    fun saveSchedule() = launch {
        log(TAG) { "saveSchedule()" }

        val state = internalState.value()
        require(state.canSave)

        val toEdit = state.existing ?: Schedule(id = scheduleId)

        val updated = toEdit.copy(
            label = state.label!!,
            hour = state.hour!!,
            minute = state.minute!!,
            repeatInterval = state.repeatInterval,
        )
        schedulerManager.saveSchedule(updated)
        popNavStack()
    }

    fun updateTime(hour: Int, minute: Int) {
        internalState.updateAsync {
            copy(
                hour = hour,
                minute = minute,
            )
        }
    }

    fun updateLabel(label: String) {
        internalState.updateAsync {
            copy(label = label)
        }
    }

    fun decreasedays() {
        internalState.updateAsync {
            val dur = repeatInterval.minusDays(1).coerceAtLeast(Duration.ofDays(1))
            copy(repeatInterval = dur)
        }
    }

    fun increaseDays() {
        internalState.updateAsync {
            val dur = repeatInterval.plusDays(1).coerceAtMost(Duration.ofDays(21))
            copy(repeatInterval = dur)
        }
    }

    data class State(
        val existing: Schedule?,
        val label: String?,
        val hour: Int?,
        val minute: Int?,
        val repeatInterval: Duration = Duration.ofDays(3),
    ) {
        val canSave: Boolean
            get() = label != null && hour != null && minute != null

    }

    companion object {
        private val TAG = logTag("Scheduler", "Schedule", "ViewModel")
    }

}
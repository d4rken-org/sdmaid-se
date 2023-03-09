package eu.darken.sdmse.scheduler.ui.manager.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
class ScheduleItemDialogVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val schedulerManager: SchedulerManager,
) : ViewModel3(dispatcherProvider) {
    private val navArgs by handle.navArgs<ScheduleItemDialogArgs>()

    private val scheduleId: String = navArgs.scheduleId

    private val internalState = DynamicStateFlow<State>(parentScope = viewModelScope) {
        val existing = schedulerManager.getSchedule(scheduleId)
        State(
            existing = existing,
            label = existing?.label,
            hour = existing?.hour,
            minute = existing?.minute,
            repeatMillis = existing?.repeatIntervalMs ?: Duration.ofDays(3).toMillis(),
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
            repeatIntervalMs = state.repeatMillis,
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
            val dur = Duration.ofMillis(repeatMillis).minusDays(1).coerceAtLeast(Duration.ofDays(1))
            copy(repeatMillis = dur.toMillis())
        }
    }

    fun increaseDays() {
        internalState.updateAsync {
            val dur = Duration.ofMillis(repeatMillis).plusDays(1).coerceAtMost(Duration.ofDays(21))
            copy(repeatMillis = dur.toMillis())
        }
    }

    data class State(
        val existing: Schedule?,
        val label: String?,
        val hour: Int?,
        val minute: Int?,
        val repeatMillis: Long = Duration.ofDays(3).toMillis(),
    ) {
        val canSave: Boolean
            get() = label != null && hour != null && minute != null

    }

    companion object {
        private val TAG = logTag("Scheduler", "Schedule", "Dialog", "VM")
    }

}
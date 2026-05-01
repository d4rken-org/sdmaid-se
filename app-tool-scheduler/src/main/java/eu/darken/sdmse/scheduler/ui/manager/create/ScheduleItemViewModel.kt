package eu.darken.sdmse.scheduler.ui.manager.create

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import javax.inject.Inject

/**
 * Edit form for a single Schedule.
 *
 * Architecture: the form is an *editable draft*. We don't derive [state] from
 * [schedulerManager.state] directly — that would let an external schedule save while the user is
 * typing wipe their input. Instead we initialize [draft] once from the live schedule lookup and
 * never re-sync from upstream after that.
 *
 * `scheduleId` is bound by the host via [setScheduleId] (Picker-style entry-arg binding).
 * `SavedStateHandle.toRoute<>()` is unreliable for non-nullable serializable args under Nav3 — see
 * project memory `feedback_nav3_route_binding`.
 */
@HiltViewModel
class ScheduleItemViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val schedulerManager: SchedulerManager,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val scheduleIdFlow = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<FormDraft?>(null)
    private val isCreateMode = MutableStateFlow<Boolean?>(null)

    fun setScheduleId(id: String) {
        if (scheduleIdFlow.value == id) return
        scheduleIdFlow.value = id
        launch {
            // Wait for the id to land, then resolve initial form values from the live schedule.
            val resolvedId = scheduleIdFlow.filterNotNull().first()
            val initial = schedulerManager.state.first().schedules.firstOrNull { it.id == resolvedId }
            isCreateMode.value = (initial == null)
            draft.value = FormDraft(
                label = initial?.label?.takeIf { it.isNotBlank() },
                hour = initial?.hour,
                minute = initial?.minute,
                repeatInterval = initial?.repeatInterval ?: Duration.ofDays(3),
            )
        }
    }

    val state: StateFlow<State> = draft
        .filterNotNull()
        .map { d ->
            State(
                label = d.label,
                hour = d.hour,
                minute = d.minute,
                repeatInterval = d.repeatInterval,
                canSave = !d.label.isNullOrBlank() && d.hour != null && d.minute != null,
                isReady = true,
            )
        }
        .safeStateIn(initialValue = State(), onError = { State(isReady = false) })

    fun updateLabel(label: String) {
        draft.update { current -> current?.copy(label = label.takeIf { it.isNotBlank() }) }
    }

    fun updateTime(hour: Int, minute: Int) {
        draft.update { current -> current?.copy(hour = hour, minute = minute) }
    }

    fun decreaseDays() {
        draft.update { current ->
            current?.copy(
                repeatInterval = current.repeatInterval.minusDays(1).coerceAtLeast(Duration.ofDays(1)),
            )
        }
    }

    fun increaseDays() {
        draft.update { current ->
            current?.copy(
                repeatInterval = current.repeatInterval.plusDays(1).coerceAtMost(Duration.ofDays(21)),
            )
        }
    }

    fun saveSchedule() = launch {
        log(TAG) { "saveSchedule()" }
        val d = draft.value ?: return@launch
        if (d.label.isNullOrBlank() || d.hour == null || d.minute == null) {
            log(TAG, WARN) { "saveSchedule: incomplete draft, ignoring" }
            return@launch
        }
        val id = scheduleIdFlow.value ?: return@launch
        val isCreate = isCreateMode.value ?: return@launch
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id }
        if (!isCreate && live == null) {
            // Edit was started for an existing schedule that has since been deleted. Abort silently
            // rather than resurrecting the schedule from a stale form.
            log(TAG, WARN) { "saveSchedule: schedule $id was deleted, aborting" }
            navUp()
            return@launch
        }
        if (live != null && live.isEnabled) {
            // Schedule was enabled externally between sheet open and save. SchedulerManager only
            // re-checks WorkManager on scheduledAt change, not on hour/minute change, so saving
            // would diverge displayed time from the actual WorkManager delay.
            log(TAG, WARN) { "saveSchedule: schedule $id became enabled, aborting" }
            navUp()
            return@launch
        }
        val toEdit = live ?: Schedule(id = id)
        schedulerManager.saveSchedule(
            toEdit.copy(
                label = d.label,
                hour = d.hour,
                minute = d.minute,
                repeatInterval = d.repeatInterval,
            ),
        )
        navUp()
    }

    private data class FormDraft(
        val label: String?,
        val hour: Int?,
        val minute: Int?,
        val repeatInterval: Duration,
    )

    data class State(
        val label: String? = null,
        val hour: Int? = null,
        val minute: Int? = null,
        val repeatInterval: Duration = Duration.ofDays(3),
        val canSave: Boolean = false,
        val isReady: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Scheduler", "Schedule", "ViewModel")
    }
}

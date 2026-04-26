package eu.darken.sdmse.scheduler.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BatteryHelper
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.scheduler.R
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleId
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import eu.darken.sdmse.scheduler.ui.ScheduleItemRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SchedulerManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    private val schedulerManager: SchedulerManager,
    private val settings: SchedulerSettings,
    private val upgradeRepo: UpgradeRepo,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val batteryHelper: BatteryHelper,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private val showBatteryOptimizationHint = combine(
        batteryHelper.isIgnoringBatteryOptimizations,
        settings.hintBatteryDismissed.flow,
    ) { isIgnoring, isDismissed ->
        !isDismissed && !isIgnoring
    }

    init {
        // One-shot: create a default schedule the first time the screen opens with no entries.
        schedulerManager.state
            .take(1)
            .onEach { state ->
                if (state.schedules.isNotEmpty()) return@onEach
                if (settings.createdDefaultEntry.value()) return@onEach

                val defaultEntry = Schedule(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.scheduler_schedule_default_name),
                )
                schedulerManager.saveSchedule(defaultEntry)
                settings.createdDefaultEntry.value(true)
            }
            .launchInViewModel()

        // One-shot: re-arm the battery hint if optimization is currently being ignored.
        launch {
            if (batteryHelper.isIgnoringBatteryOptimizations.first() && settings.hintBatteryDismissed.value()) {
                log(TAG) { "Resetting hintBatteryDismissed to false" }
                settings.hintBatteryDismissed.value(false)
            }
        }
    }

    val state: StateFlow<State> = combine(
        schedulerManager.state,
        showBatteryOptimizationHint,
    ) { schedulerState, showBatteryHint ->
        val sortedSchedules = schedulerState.schedules.sortedBy { it.label.lowercase() }
        // showCommands re-resolved per-emission via the suspend `combine` block; this only
        // refreshes when schedulerManager.state re-emits, so root/ADB becoming available mid-screen
        // won't surface until the next emission. Same trade-off as legacy.
        val showCommands = rootManager.canUseRootNow() || adbManager.canUseAdbNow()
        State(
            schedules = sortedSchedules,
            showAlarmHint = sortedSchedules.any { it.isEnabled },
            showBatteryHint = hasApiLevel(31) && showBatteryHint && sortedSchedules.any { it.isEnabled },
            showCommands = showCommands,
            isLoading = false,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State(isLoading = false) },
    )

    fun createNew() {
        log(TAG) { "createNew()" }
        navTo(ScheduleItemRoute(scheduleId = UUID.randomUUID().toString()))
    }

    fun editSchedule(id: ScheduleId) {
        log(TAG) { "editSchedule($id)" }
        navTo(ScheduleItemRoute(scheduleId = id))
    }

    fun removeSchedule(id: ScheduleId) = launch {
        log(TAG) { "removeSchedule($id)" }
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id } ?: return@launch
        if (live.isEnabled) {
            log(TAG, WARN) { "removeSchedule: schedule $id is enabled, ignoring" }
            return@launch
        }
        schedulerManager.removeSchedule(id)
    }

    fun toggleSchedule(id: ScheduleId) = launch {
        log(TAG) { "toggleSchedule($id)" }
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id } ?: return@launch
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val enabling = !live.isEnabled
        schedulerManager.saveSchedule(
            live.copy(
                scheduledAt = if (enabling) Instant.now() else null,
                userZone = if (enabling) ZoneId.systemDefault().id else live.userZone,
            ),
        )
    }

    fun toggleCorpseFinder(id: ScheduleId) = launch { toggleTool(id) { it.copy(useCorpseFinder = !it.useCorpseFinder) } }

    fun toggleSystemCleaner(id: ScheduleId) = launch { toggleTool(id) { it.copy(useSystemCleaner = !it.useSystemCleaner) } }

    fun toggleAppCleaner(id: ScheduleId) = launch { toggleTool(id) { it.copy(useAppCleaner = !it.useAppCleaner) } }

    private suspend fun toggleTool(id: ScheduleId, mutate: (Schedule) -> Schedule) {
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id } ?: return
        // Tool toggles should not flip while the schedule is enabled — the row UI disables them,
        // but a race between bind and click is possible.
        if (live.isEnabled) {
            log(TAG, WARN) { "toggleTool: schedule $id is enabled, ignoring" }
            return
        }
        schedulerManager.saveSchedule(mutate(live))
    }

    fun requestEditCommands(id: ScheduleId) = launch {
        log(TAG) { "requestEditCommands($id)" }
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id } ?: return@launch
        events.emit(Event.EditCommands(id, live.commandsAfterSchedule.joinToString("\n")))
    }

    fun requestBatteryOptimizationSettings() {
        log(TAG) { "requestBatteryOptimizationSettings()" }
        events.tryEmit(Event.ShowBatteryOptimizationSettings(batteryHelper.createIntent()))
    }

    fun onBatteryIntentFailed(throwable: Throwable) {
        log(TAG, WARN) { "onBatteryIntentFailed: $throwable" }
        errorEvents.tryEmit(throwable)
    }

    fun dismissBatteryHint() = launch {
        log(TAG) { "dismissBatteryHint()" }
        settings.hintBatteryDismissed.value(true)
    }

    fun showHelp() {
        log(TAG) { "showHelp()" }
        webpageTool.open(HELP_URL)
    }

    fun updateCommandsAfterSchedule(id: ScheduleId, rawCmdInput: String) = launch {
        log(TAG) { "updateCommandsAfterSchedule($id, $rawCmdInput)" }
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id } ?: return@launch
        if (live.isEnabled) {
            log(TAG, WARN) { "updateCommandsAfterSchedule: schedule $id is enabled, ignoring" }
            return@launch
        }
        val cmds = rawCmdInput
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        log(TAG, INFO) { "New commands to execute after schedule: $cmds" }
        schedulerManager.saveSchedule(live.copy(commandsAfterSchedule = cmds))
    }

    fun debugSchedule() = launch {
        log(TAG) { "debugSchedule()" }
        val id = UUID.randomUUID().toString()
        val now = LocalTime.now().plusMinutes(1)
        val testSchedule = Schedule(
            id = id,
            label = "Test Schedule $id",
            hour = now.hour,
            minute = now.minute,
            repeatInterval = Duration.ofDays(1),
            scheduledAt = Instant.now(),
            userZone = ZoneId.systemDefault().id,
        )
        schedulerManager.saveSchedule(testSchedule)
    }

    data class State(
        val schedules: List<Schedule> = emptyList(),
        val showAlarmHint: Boolean = false,
        val showBatteryHint: Boolean = false,
        val showCommands: Boolean = false,
        val isLoading: Boolean = true,
    )

    sealed interface Event {
        data class EditCommands(val scheduleId: ScheduleId, val initialText: String) : Event
        data class ShowBatteryOptimizationSettings(val intent: Intent) : Event
    }

    companion object {
        private val TAG = logTag("Scheduler", "Manager", "ViewModel")
        private const val HELP_URL = "https://github.com/d4rken-org/sdmaid-se/wiki/Scheduler"
    }
}

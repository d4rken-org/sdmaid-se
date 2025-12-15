package eu.darken.sdmse.scheduler.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BatteryHelper
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleId
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import eu.darken.sdmse.scheduler.ui.manager.items.AlarmHintRowVH
import eu.darken.sdmse.scheduler.ui.manager.items.BatteryHintRowVH
import eu.darken.sdmse.scheduler.ui.manager.items.ScheduleRowVH
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SchedulerManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    taskManager: TaskManager,
    private val schedulerManager: SchedulerManager,
    private val settings: SchedulerSettings,
    private val upgradeRepo: UpgradeRepo,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val batteryHelper: BatteryHelper,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<SchedulerManagerEvents>()

    private val showBatteryOptimizationHint = combine(
        batteryHelper.isIgnoringBatteryOptimizations,
        settings.hintBatteryDismissed.flow,
    ) { isIgnoring, isDismissed ->
        !isDismissed && !isIgnoring
    }

    init {
        schedulerManager.state
            .take(1)
            .onEach {
                if (it.schedules.isNotEmpty()) return@onEach
                if (settings.createdDefaultEntry.value()) return@onEach

                val defaultEntry = Schedule(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.scheduler_schedule_default_name),
                )
                schedulerManager.saveSchedule(defaultEntry)
                settings.createdDefaultEntry.value(true)
            }
            .launchInViewModel()

        launch {
            if (batteryHelper.isIgnoringBatteryOptimizations.first() && settings.hintBatteryDismissed.value()) {
                log(TAG) { "Resetting hintBatteryDismissed to false" }
                settings.hintBatteryDismissed.value(false)
            }
        }
    }

    val items = combine(
        schedulerManager.state,
        taskManager.state,
        showBatteryOptimizationHint,
    ) { schedulerState, _, showBatteryHint ->
        val items = mutableListOf<SchedulerAdapter.Item>()

        if (hasApiLevel(31) && showBatteryHint && schedulerState.schedules.any { it.isEnabled }) {
            BatteryHintRowVH.Item(
                onFix = {
                    events.postValue(
                        SchedulerManagerEvents.ShowBatteryOptimizationSettings(batteryHelper.createIntent())
                    )
                },
                onDismiss = { settings.hintBatteryDismissed.valueBlocking = true }
            ).apply { items.add(this) }
        }

        val showCommands = rootManager.canUseRootNow() || adbManager.canUseAdbNow()

        schedulerState.schedules.sortedBy { it.label.lowercase() }.map { schedule ->
            ScheduleRowVH.Item(
                schedule = schedule,
                onEdit = {
                    SchedulerManagerFragmentDirections.actionSchedulerManagerFragmentToScheduleItemDialog(
                        scheduleId = schedule.id
                    ).navigate()
                },
                onToggle = {
                    launch {
                        if (upgradeRepo.isPro()) {
                            schedulerManager.saveSchedule(
                                schedule.copy(scheduledAt = if (!schedule.isEnabled) Instant.now() else null)
                            )
                        } else {
                            MainDirections.goToUpgradeFragment().navigate()
                        }
                    }
                },
                onRemove = {
                    launch { schedulerManager.removeSchedule(schedule.id) }
                },
                onToggleCorpseFinder = {
                    launch {
                        schedulerManager.saveSchedule(schedule.copy(useCorpseFinder = !schedule.useCorpseFinder))
                    }
                },
                onToggleSystemCleaner = {
                    launch {
                        schedulerManager.saveSchedule(schedule.copy(useSystemCleaner = !schedule.useSystemCleaner))
                    }
                },
                onToggleAppCleaner = {
                    launch {
                        schedulerManager.saveSchedule(schedule.copy(useAppCleaner = !schedule.useAppCleaner))
                    }
                },
                onEditFinalCommands = {
                    events.postValue(SchedulerManagerEvents.FinalCommandsEdit(schedule))
                },
                showCommands = showCommands,
            )
        }.run { items.addAll(this) }

        if (schedulerState.schedules.any { it.isEnabled }) {
            AlarmHintRowVH.Item(
                schedulerState
            ).apply { items.add(this) }
        }

        State(
            listItems = items,
        )
    }
        .onStart { emit(State()) }
        .asLiveData2()

    fun createNew() {
        log(TAG) { "createNew()" }
        SchedulerManagerFragmentDirections.actionSchedulerManagerFragmentToScheduleItemDialog(
            scheduleId = UUID.randomUUID().toString()
        ).navigate()
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
        )
        schedulerManager.saveSchedule(testSchedule)
    }

    fun updateCommandsAfterSchedule(id: ScheduleId, rawCmdInput: String) = launch {
        log(TAG) { "updateCommandsAfterSchedule($id,$rawCmdInput)" }
        val cmds = rawCmdInput
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        log(TAG, INFO) { "New commands to execute after schedule: $cmds" }
        val updatedSchedule = schedulerManager.getSchedule(id)!!.copy(commandsAfterSchedule = cmds)
        schedulerManager.saveSchedule(updatedSchedule)
    }

    data class State(
        val listItems: List<SchedulerAdapter.Item>? = null,
    )

    companion object {
        private val TAG = logTag("Scheduler", "Manager", "ViewModel")
    }
}
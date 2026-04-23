package eu.darken.sdmse.scheduler.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BatteryHelper
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.scheduler.R
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleId
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import eu.darken.sdmse.scheduler.ui.ScheduleItemRoute
import eu.darken.sdmse.scheduler.ui.manager.items.AlarmHintRowVH
import eu.darken.sdmse.scheduler.ui.manager.items.BatteryHintRowVH
import eu.darken.sdmse.scheduler.ui.manager.items.ScheduleRowVH
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SchedulerManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    taskSubmitter: TaskSubmitter,
    private val schedulerManager: SchedulerManager,
    private val settings: SchedulerSettings,
    private val upgradeRepo: UpgradeRepo,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val batteryHelper: BatteryHelper,
    private val shellOps: ShellOps,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<SchedulerManagerEvents>()

    private val fixInProgress = AtomicBoolean(false)

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
        taskSubmitter.state,
        showBatteryOptimizationHint,
    ) { schedulerState, _, showBatteryHint ->
        val items = mutableListOf<SchedulerAdapter.Item>()

        if (hasApiLevel(31) && showBatteryHint && schedulerState.schedules.any { it.isEnabled }) {
            BatteryHintRowVH.Item(
                onFix = { fixBatteryOptimization() },
                onDismiss = { settings.hintBatteryDismissed.valueBlocking = true }
            ).apply { items.add(this) }
        }

        val showCommands = rootManager.canUseRootNow() || adbManager.canUseAdbNow()

        schedulerState.schedules.sortedBy { it.label.lowercase() }.map { schedule ->
            ScheduleRowVH.Item(
                schedule = schedule,
                onEdit = {
                    navigateTo(ScheduleItemRoute(scheduleId = schedule.id))
                },
                onToggle = {
                    launch {
                        if (upgradeRepo.isPro()) {
                            val enabling = !schedule.isEnabled
                            schedulerManager.saveSchedule(
                                schedule.copy(
                                    scheduledAt = if (enabling) Instant.now() else null,
                                    userZone = if (enabling) ZoneId.systemDefault().id else schedule.userZone,
                                )
                            )
                        } else {
                            navigateTo(UpgradeRoute())
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
        navigateTo(ScheduleItemRoute(scheduleId = UUID.randomUUID().toString()))
    }

    fun fixBatteryOptimization() = launch {
        if (!fixInProgress.compareAndSet(false, true)) {
            log(TAG) { "fixBatteryOptimization(): already in progress, ignoring" }
            return@launch
        }
        try {
            val modes = buildList {
                if (rootManager.canUseRootNow()) add(ShellOps.Mode.ROOT)
                if (adbManager.canUseAdbNow()) add(ShellOps.Mode.ADB)
            }
            val pkg = context.packageName
            val cmd = ShellOpsCmd("/system/bin/cmd deviceidle whitelist +$pkg")

            for (mode in modes) {
                val result = try {
                    withTimeoutOrNull(15_000L) { shellOps.execute(cmd, mode) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "fixBatteryOptimization(mode=$mode) threw: ${e.asLog()}" }
                    null
                }

                if (result != null && result.exitCode == 0) {
                    log(TAG, INFO) { "fixBatteryOptimization(mode=$mode) exit=0, verifying…" }
                    val verified = withTimeoutOrNull(5_000L) {
                        batteryHelper.isIgnoringBatteryOptimizations.filter { it }.firstOrNull()
                    } == true
                    if (verified) {
                        log(TAG, INFO) { "fixBatteryOptimization(mode=$mode) verified whitelisted" }
                        return@launch
                    }
                    // Exit=0 but OS still says not-whitelisted (e.g. MIUI/ColorOS silently ignoring).
                    // Another privileged mode running the same command won't help — go straight to Settings.
                    log(TAG, WARN) { "fixBatteryOptimization(mode=$mode) exit=0 but OS still reports not-whitelisted; falling back" }
                    break
                } else {
                    log(TAG, WARN) { "fixBatteryOptimization(mode=$mode) failed: $result" }
                }
            }

            log(TAG) { "fixBatteryOptimization(): no privileged mode succeeded, opening settings" }
            events.postValue(
                SchedulerManagerEvents.ShowBatteryOptimizationSettings(batteryHelper.createIntent())
            )
        } finally {
            fixInProgress.set(false)
        }
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
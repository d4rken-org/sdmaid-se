package eu.darken.sdmse.scheduler.ui.manager

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import eu.darken.sdmse.scheduler.ui.manager.items.AlarmHintRowVH
import eu.darken.sdmse.scheduler.ui.manager.items.ScheduleRowVH
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.*
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SchedulerManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val taskManager: TaskManager,
    private val schedulerManager: SchedulerManager,
    private val schedulerSettings: SchedulerSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    init {
        schedulerManager.state
            .take(1)
            .onEach {
                if (it.schedules.isNotEmpty()) return@onEach
                if (schedulerSettings.createdDefaultEntry.value()) return@onEach

                val defaultEntry = Schedule(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.scheduler_schedule_default_name),
                )
                schedulerManager.saveSchedule(defaultEntry)
                schedulerSettings.createdDefaultEntry.value(true)
            }
            .launchInViewModel()
    }

    val items = combine(
        schedulerManager.state,
        taskManager.state
    ) { schedulerState, taskState ->
        val items = mutableListOf<SchedulerAdapter.Item>()

        if (schedulerState.schedules.any { it.isEnabled }) {
            items.add(AlarmHintRowVH.Item(schedulerState))
        }

        schedulerState.schedules.map { schedule ->
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
                }
            )
        }.run { items.addAll(this) }

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

    data class State(
        val listItems: List<SchedulerAdapter.Item>? = null,
    )

    companion object {
        private val TAG = logTag("Scheduler", "Manager", "ViewModel")
    }
}
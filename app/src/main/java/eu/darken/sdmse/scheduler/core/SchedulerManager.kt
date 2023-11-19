package eu.darken.sdmse.scheduler.core

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.common.toSystemTimezone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SchedulerManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val settings: SchedulerSettings,
    private val storage: ScheduleStorage,
    private val workManager: WorkManager,
) {

    private val internalState = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        State(schedules = storage.load() ?: emptySet())
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .drop(1)
            .onEach { storage.save(it.schedules) }
            .catch { log(TAG, ERROR) { "Failed to save schedules: ${it.asLog()}" } }
            .launchIn(appScope)

        // Check if the enabled state of any schedule changed
        internalState.flow
            .withPrevious()
            .filter { (oldState, newState) ->
                if (oldState == null) return@filter true
                val oldMap = oldState.schedules.associateBy { it.id }
                val newMap = newState.schedules.associateBy { it.id }

                for ((id, newItem) in newMap) {
                    val oldItem = oldMap[id] ?: return@filter true
                    if (oldItem.scheduledAt != newItem.scheduledAt) return@filter true
                }
                return@filter false
            }
            .map { it.second }
            .onEach { newState ->
                log(TAG, INFO) { "Scheduler data changed, re-checking states." }
                newState.schedules.forEach { it.checkSchedulingState() }
            }
            .launchIn(appScope)

        // Check if global options have changed that require schedule re-creation
        combine(
            settings.skipWhenNotCharging.flow.distinctUntilChanged().withPrevious(),
            settings.skipWhenPowerSaving.flow.distinctUntilChanged().withPrevious(),
        ) { (oldCharge, newCharge), (oldSaving, newSaving) ->
            if (oldCharge == null && oldSaving == null) {
                // We don't act on init/first-subscribe
                return@combine
            }
            if (oldCharge == newCharge && oldSaving == newSaving) return@combine

            log(TAG) { "Global scheduler settings changed, recreating scheduled work." }
            state.first().schedules.forEach {
                if (it.isScheduled()) {
                    it.cancel()
                    it.schedule(reschedule = false)
                }
            }
        }.launchIn(appScope)
    }

    data class State(
        val schedules: Set<Schedule>,
    )

    private val Schedule.workName: String
        get() = "worker-schedule-$id"

    private suspend fun Schedule.isScheduled(): Boolean =
        workManager.getWorkInfosForUniqueWork(workName).await()
            ?.any { infos -> infos.state == WorkInfo.State.ENQUEUED || infos.state == WorkInfo.State.RUNNING }
            ?: false

    private suspend fun Schedule.cancel() {
        workManager.cancelUniqueWork(workName).await()
        if (isScheduled()) log(TAG, WARN) { "Failed to cancel $this" }
    }

    suspend fun reschedule(id: ScheduleId) {
        log(TAG, INFO) { "Rescheduling $id" }
        getSchedule(id)!!.schedule(reschedule = true)
    }

    private suspend fun Schedule.schedule(reschedule: Boolean) {
        log(TAG, INFO) { "schedule($label): (reschedule=$reschedule) $this" }

        val now = Instant.now()

        val executionEta = calcExecutionEta(now, reschedule)
            ?: throw IllegalArgumentException("Can't schedule 'unscheduled' Schedule...")

        val executionAt = now.plus(executionEta)
        val executionAtZoned = executionAt.toSystemTimezone()
        log(TAG) { "schedule($label): Next execution in $executionEta @ $executionAt ($executionAtZoned)" }

        val workRequest = OneTimeWorkRequestBuilder<SchedulerWorker>().apply {
            addTag("Label: $label")

            // https://issuetracker.google.com/issues?q=setInitialDelay%20
            // Hmm https://issuetracker.google.com/issues/155936231
            setInitialDelay(executionEta)

            setConstraints(
                Constraints.Builder().apply {
                    val noLowBattery = settings.skipWhenPowerSaving.value()
                    setRequiresBatteryNotLow(noLowBattery)
                    val onlyWhenCharging = settings.skipWhenNotCharging.value()
                    setRequiresCharging(onlyWhenCharging)
                }.build()
            )

            setInputData(workDataOf(SchedulerWorker.INPUT_SCHEDULE_ID to id))
        }.build()

        workManager.enqueueUniqueWork(
            workName,
            if (reschedule) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            workRequest
        ).await()

        if (isScheduled()) {
            log(TAG, INFO) { "schedule($label): Scheduled for $executionAt" }
        } else {
            log(TAG, WARN) { "schedule($label): Failed to schedule" }
        }
    }

    private suspend fun Schedule.checkSchedulingState() {
        val isScheduled = isScheduled()

        if (isEnabled && !isScheduled) {
            log(TAG, INFO) { "checkSchedulingState($id): Enabled, but not scheduled. Scheduling $this" }
            schedule(reschedule = false)
        } else if (!isEnabled && isScheduled) {
            log(TAG, INFO) { "checkSchedulingState($id): Disabled, but scheduled. Canceling $this" }
            cancel()
        } else {
            log(TAG) { "checkSchedulingState($id): State is correct (isScheduled=$isScheduled) for $this" }
        }
    }

    suspend fun getSchedule(scheduleId: ScheduleId): Schedule? {
        return state.first().schedules.singleOrNull { it.id == scheduleId }
    }

    suspend fun saveSchedule(schedule: Schedule) = internalState.updateBlocking {
        log(TAG) { "saveSchedule(): $schedule" }
        val newSchedules = this.schedules.filter { it.id != schedule.id }.plus(schedule).toSet()
        copy(schedules = newSchedules)
    }

    suspend fun removeSchedule(scheduleId: ScheduleId) = internalState.updateBlocking {
        log(TAG) { "removeSchedule(): $scheduleId" }

        val target = this.schedules.singleOrNull { it.id == scheduleId }
        if (target == null) {
            log(TAG, ERROR) { "Can't find $scheduleId" }
            return@updateBlocking this
        }

        target.cancel()

        copy(schedules = this.schedules.minus(target).toSet())
    }

    suspend fun updateExecutedNow(id: ScheduleId) = internalState.updateBlocking {
        log(TAG) { "updateExecutedNow(): $id" }
        val target = this.schedules.singleOrNull { it.id == id }
        if (target == null) {
            log(TAG, ERROR) { "Can't find $id" }
            return@updateBlocking this
        }

        val updatedTarget = target.copy(executedAt = Instant.now())

        copy(schedules = this.schedules.minus(target).plus(updatedTarget).toSet())
    }

    companion object {
        internal val TAG = logTag("Scheduler", "Manager")
    }
}
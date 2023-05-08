package eu.darken.sdmse.scheduler.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SchedulerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val settings: SchedulerSettings,
    private val storage: ScheduleStorage,
    private val alarmManager: AlarmManager,
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

        internalState.flow
            .onEach { st ->
                st.schedules.forEach {
                    it.checkSchedulingState()
                }
            }
            .launchIn(appScope)
    }

    data class State(
        val schedules: Set<Schedule>,
    )

    private fun Schedule.createIntent(): Intent = Intent(context, SchedulerReceiver::class.java).apply {
        action = SCHEDULE_INTENT
        data = Uri.parse("scheduler://alarm.$id")
    }

    private fun Schedule.createPendingIntent(flags: Int = 0): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        createIntent(),
        PendingIntentCompat.FLAG_IMMUTABLE or flags
    )

    private fun Schedule.isScheduled(): Boolean = createPendingIntent(PendingIntent.FLAG_NO_CREATE) != null

    private fun Schedule.cancel() {
        val pi = createPendingIntent()!!
        alarmManager.cancel(pi)
        pi.cancel()
        if (isScheduled()) log(TAG, WARN) { "Failed to cancel $this" }
    }

    private fun Schedule.schedule() {
        requireNotNull(scheduledAt) { "Can't schedule 'unscheduled' Schedule..." }

        val triggerTime = firstExecution!!

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime.toEpochMilli(),
            repeatInterval.toMillis(),
            createPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        if (isScheduled()) log(TAG) { "Scheduled for $triggerTime : $this" }
        else log(TAG, WARN) { "Failed to schedule $this" }
    }

    private fun Schedule.checkSchedulingState() {
        val isScheduled = isScheduled()
        log(TAG) { "Checking ($isScheduled): $this" }

        if (isEnabled && !isScheduled) {
            log(TAG) { "Enabled, but not scheduled. Scheduling $this" }
            schedule()
        } else if (!isEnabled && isScheduled) {
            log(TAG) { "Disabled, but scheduled. Canceling $this" }
            cancel()
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
        private const val REQUEST_CODE = 1000
        const val SCHEDULE_INTENT = "scheduler.schedule.intent"
        internal val TAG = logTag("Scheduler", "Manager")
    }
}
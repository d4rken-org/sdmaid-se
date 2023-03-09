package eu.darken.sdmse.scheduler.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
        State(
            schedules = storage.load()
        )
    }

    private fun Schedule.createIntent(): Intent = Intent(context, SchedulerReceiver::class.java).apply {
        action = SCHEDULE_INTENT
        data = Uri.parse("custom://scheduler.alarm.$id")
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
        requireNotNull(scheduledAt)
        val triggerTime = LocalDateTime.ofInstant(scheduledAt, ZoneOffset.systemDefault())
            .withHour(hour)
            .withMinute(minute)
            .let {
                if (it.isAfter(LocalDateTime.now())) {
                    it.plus(repeatInterval.minus(Duration.ofDays(1)))
                } else {
                    it.plus(repeatInterval)
                }
            }
            .atZone(ZoneId.systemDefault())
            .toInstant()

        log(TAG) { "Scheduling for $triggerTime : $this" }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime.toEpochMilli(),
            repeatInterval.toMillis(),
            createPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)
        )
        if (!isScheduled()) log(TAG, WARN) { "Failed to schedule $this" }
    }

    init {
        internalState.flow
            .onEach { st ->
                st.schedules.map { schedule ->
                    val isScheduled = schedule.isScheduled()
                    log(TAG) { "Checking ($isScheduled): $schedule" }

                    if (schedule.isEnabled && !isScheduled) {
                        log(TAG) { "Enabled, but not scheduled. Scheduling $schedule" }
                        schedule.schedule()
                    } else if (!schedule.isEnabled && isScheduled) {
                        log(TAG) { "Disabled, but scheduled. Canceling $schedule" }
                        schedule.cancel()
                    }
                }

            }
            .launchIn(appScope)
    }

    data class State(
        val schedules: Collection<Schedule>,
    )

    val state: Flow<State> = internalState.flow

    suspend fun getSchedule(scheduleId: String): Schedule? {
        return state.first().schedules.singleOrNull { it.id == scheduleId }
    }

    suspend fun saveSchedule(schedule: Schedule) = withContext(NonCancellable) {
        log(TAG) { "saveSchedule(): $schedule" }
        val newSchedules = storage.load().filter { it.id != schedule.id }.plus(schedule).toSet()
        storage.save(newSchedules)
        internalState.updateBlocking {
            copy(schedules = newSchedules)
        }
    }

    suspend fun removeSchedule(scheduleId: String) = withContext(NonCancellable) {
        log(TAG) { "removeSchedule(): $scheduleId" }

        val current = storage.load()
        val target = current.singleOrNull { it.id == scheduleId }
        if (target == null) {
            log(TAG, Logging.Priority.ERROR) { "Can't find $scheduleId" }
            return@withContext
        }

        val newSchedules = current.minus(target).toSet()
        storage.save(newSchedules)
        internalState.updateBlocking {
            copy(schedules = newSchedules)
        }

        target.cancel()
    }

    companion object {
        private const val REQUEST_CODE = 1000
        private const val SCHEDULE_INTENT = "scheduler.schedule.intent"
        internal val TAG = logTag("Scheduler", "Manager")
    }
}
package eu.darken.sdmse.scheduler.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import eu.darken.sdmse.main.ui.MainActivity
import javax.inject.Inject


class SchedulerNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scheduler_notification_channel_label),
            NotificationManager.IMPORTANCE_LOW
        ).run { notificationManager.createNotificationChannel(this) }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntentCompat.FLAG_IMMUTABLE
        )

        builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setChannelId(CHANNEL_ID)
            setContentIntent(openPi)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.ic_notification_mascot_24)
            setOngoing(true)
            setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
            setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
        }
    }

    fun getBuilder(schedule: Schedule?): NotificationCompat.Builder {
        if (schedule == null) {
            return builder.apply {
                setStyle(null)
                setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
                setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
            }
        }

        return builder.apply {
            setContentTitle(context.getString(R.string.scheduler_notification_title))
            setContentText(context.getString(R.string.scheduler_notification_message, schedule.label))
            log(TAG) { "getBuilder(): $schedule" }
        }
    }

    fun getNotification(schedule: Schedule?): Notification = getBuilder(schedule).build()

    fun getForegroundInfo(schedule: Schedule): ForegroundInfo = getBuilder(schedule).toForegroundInfo(schedule)

    private fun NotificationCompat.Builder.toForegroundInfo(schedule: Schedule): ForegroundInfo = if (hasApiLevel(29)) {
        @Suppress("NewApi")
        ForegroundInfo(schedule.id.toNotificationid(), build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(schedule.id.toNotificationid(), build())
    }

    private fun ScheduleId.toNotificationid(): Int {
        val baseId = (this.hashCode() and Int.MAX_VALUE) % 101
        return NOTIFICATION_ID_RANGE + baseId
    }

    fun notify(schedule: Schedule) {
        val id = schedule.id.toNotificationid()
        val notification = getNotification(schedule)
        log(TAG) { "notify($id, $schedule)" }
        notificationManager.notify(id, notification)
    }

    fun cancel(scheduleId: ScheduleId) {
        val id = scheduleId.toNotificationid()
        log(TAG) { "cancel($id, $scheduleId)" }
        notificationManager.cancel(id)
    }

    companion object {
        val TAG = logTag("Scheduler", "Notifications", "Worker")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.scheduler"
        internal const val NOTIFICATION_ID_RANGE = 1000
    }
}

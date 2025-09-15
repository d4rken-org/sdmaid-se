package eu.darken.sdmse.main.core.taskmanager

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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import eu.darken.sdmse.main.ui.MainActivity
import javax.inject.Inject


class TaskWorkerNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    notificationManager: NotificationManager,
) {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tasks_activity_notification_channel_label),
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

    fun getBuilder(state: TaskManager.State?): NotificationCompat.Builder {
        if (state == null) {
            return builder.apply {
                setStyle(null)
                setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
                setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
            }
        }

        return builder.apply {
            setContentTitle(context.getString(R.string.tasks_activity_working_notification_title))

            val activeTasks = state.tasks.filter { it.isActive }
            val activeText = context.getQuantityString2(
                R.plurals.tasks_activity_active_notification_message,
                activeTasks.size
            )

            val queuedTasks = state.tasks.filter { it.isQueued }
            val queuedText = context.getQuantityString2(
                R.plurals.tasks_activity_queued_notification_message,
                queuedTasks.size
            )
            setContentText("$activeText | $queuedText")
            log(TAG, VERBOSE) { "updatingNotification(): $state" }
        }
    }

    fun getNotification(state: TaskManager.State?): Notification = getBuilder(state).build()

    fun getForegroundInfo(state: TaskManager.State?): ForegroundInfo = getBuilder(state).toForegroundInfo()

    private fun NotificationCompat.Builder.toForegroundInfo(): ForegroundInfo = if (hasApiLevel(29)) {
        @Suppress("NewApi")
        ForegroundInfo(NOTIFICATION_ID, this.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(NOTIFICATION_ID, this.build())
    }

    companion object {
        val TAG = logTag("TaskManager", "Notifications", "Worker")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.taskmanager.active"
        internal const val NOTIFICATION_ID = 1
    }
}

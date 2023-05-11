package eu.darken.sdmse.main.core.taskmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.main.ui.MainActivity
import javax.inject.Inject


class TaskResultNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    notificationManager: NotificationManager,
) {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tasks_activity_notification_channel_label),
            NotificationManager.IMPORTANCE_DEFAULT
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
            setSmallIcon(R.drawable.ic_notification_maid_happy_24)
            setOngoing(true)
            setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
            setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
            setSubText(context.getString(R.string.tasks_result_subtext))
        }
    }

    fun getBuilder(result: SDMTool.Task.Result): NotificationCompat.Builder = builder.apply {
        setContentTitle(context.getString(result.type.labelRes))

        setContentText(result.primaryInfo.get(context))
        log(TAG, VERBOSE) { "updatingNotification(): $result" }
    }

    fun getNotification(result: SDMTool.Task.Result): Notification = getBuilder(result).build()

    companion object {
        val TAG = logTag("TaskManager", "Notifications", "Result")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.taskmanager.result"
        internal const val NOTIFICATION_ID_BASE = 1000
    }
}

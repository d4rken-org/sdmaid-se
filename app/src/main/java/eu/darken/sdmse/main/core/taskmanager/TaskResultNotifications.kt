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

    private val openPendingIntent: PendingIntent

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.tasks_result_notification_channel_label),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }

        val openIntent = Intent(context, MainActivity::class.java)
        openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntentCompat.FLAG_IMMUTABLE
        )
    }

    fun getNotification(task: TaskSubmitter.ManagedTask): Notification {
        val body = task.result?.primaryInfo?.get(context)
            ?: context.getString(R.string.tasks_result_failed_msg)

        log(TAG, VERBOSE) { "getNotification(): $task" }

        // Fresh builder per call: a singleton builder would let two concurrent posts interleave
        // title/body state and stale fields (style, actions, …) would leak between notifications.
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setChannelId(CHANNEL_ID)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_notification_maid_happy_24)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSubText(context.getString(R.string.tasks_result_subtext))
            .setContentTitle(context.getString(task.toolType.labelRes))
            .setContentText(body)
            .build()
    }

    companion object {
        val TAG = logTag("TaskManager", "Notifications", "Result")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.taskmanager.result"
        private const val NOTIFICATION_ID_BASE = 2000

        // Explicit per-type ID mapping — do NOT use SDMTool.Type.ordinal: enum reorders
        // would silently move the IDs and risk collisions with other ranges.
        // Scheduler owns 1000-1100 (state) and 1200+ (result); this range stays clear.
        fun notificationIdFor(type: SDMTool.Type): Int = NOTIFICATION_ID_BASE + when (type) {
            SDMTool.Type.CORPSEFINDER -> 0
            SDMTool.Type.SYSTEMCLEANER -> 1
            SDMTool.Type.APPCLEANER -> 2
            SDMTool.Type.APPCONTROL -> 3
            SDMTool.Type.ANALYZER -> 4
            SDMTool.Type.DEDUPLICATOR -> 5
            SDMTool.Type.SQUEEZER -> 6
            SDMTool.Type.SWIPER -> 7
        }
    }
}

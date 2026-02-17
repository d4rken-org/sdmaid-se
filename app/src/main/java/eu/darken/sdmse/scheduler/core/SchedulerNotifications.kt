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
import eu.darken.sdmse.common.error.localized
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.MainActivity
import javax.inject.Inject


class SchedulerNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scheduler_notification_channel_label),
            NotificationManager.IMPORTANCE_LOW
        ).run { notificationManager.createNotificationChannel(this) }
    }

    private fun getBaseBuilder() = NotificationCompat.Builder(context, CHANNEL_ID).apply {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntentCompat.FLAG_IMMUTABLE
        )

        setChannelId(CHANNEL_ID)
        setContentIntent(openPi)
        priority = NotificationCompat.PRIORITY_LOW
        setSmallIcon(R.drawable.ic_notification_mascot_24)
        setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
        setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
    }

    private fun getBaseStateBuilder() = getBaseBuilder().apply {
        setOngoing(true)
    }

    private fun getBaseResultBuilder() = getBaseBuilder().apply {
        setOngoing(false)
    }

    private fun getStateBuilder(schedule: Schedule?): NotificationCompat.Builder {
        if (schedule == null) {
            return getBaseStateBuilder().apply {
                setStyle(null)
                setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
                setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
            }
        }

        return getBaseStateBuilder().apply {
            setContentTitle(context.getString(R.string.scheduler_notification_title))
            setContentText(context.getString(R.string.scheduler_notification_message, schedule.label))
            log(TAG) { "getStateBuilder(): $schedule" }
        }
    }

    private fun getStateNotification(schedule: Schedule?): Notification = getStateBuilder(schedule).build()

    fun getForegroundInfo(schedule: Schedule): ForegroundInfo = getStateBuilder(schedule).toForegroundInfo(schedule.id)

    fun getForegroundInfo(scheduleId: ScheduleId): ForegroundInfo = getStateBuilder(null).toForegroundInfo(scheduleId)

    private fun NotificationCompat.Builder.toForegroundInfo(scheduleId: ScheduleId): ForegroundInfo = if (hasApiLevel(29)) {
        @Suppress("NewApi")
        ForegroundInfo(scheduleId.toNotificationid(), build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(scheduleId.toNotificationid(), build())
    }

    private fun ScheduleId.toNotificationid(): Int {
        val baseId = (this.hashCode() and Int.MAX_VALUE) % 101
        return NOTIFICATION_ID_RANGE_STATE + baseId
    }

    fun notifyState(schedule: Schedule) {
        val id = schedule.id.toNotificationid()
        val notification = getStateNotification(schedule)
        log(TAG) { "notifyState($id, $schedule)" }
        notificationManager.notify(id, notification)
    }

    fun cancel(scheduleId: ScheduleId) {
        val id = scheduleId.toNotificationid()
        log(TAG) { "cancel($id, $scheduleId)" }
        notificationManager.cancel(id)
    }

    private fun getResultBuilder(results: Set<Results>): NotificationCompat.Builder = getBaseResultBuilder().apply {
        setContentTitle(context.getString(R.string.scheduler_notification_result_title))
        val text = if (results.any { it.error != null }) {
            val errorMsg = context.getString(R.string.scheduler_notification_result_failure_message)
            val errorTools = results
                .filter { it.error != null }
                .map {
                    val toolNameId = when (it.task.type) {
                        SDMTool.Type.CORPSEFINDER -> R.string.corpsefinder_tool_name
                        SDMTool.Type.SYSTEMCLEANER -> R.string.systemcleaner_tool_name
                        SDMTool.Type.APPCLEANER -> R.string.appcleaner_tool_name
                        SDMTool.Type.APPCONTROL -> R.string.appcontrol_tool_name
                        SDMTool.Type.ANALYZER -> R.string.analyzer_tool_name
                        SDMTool.Type.DEDUPLICATOR -> R.string.deduplicator_tool_name
SDMTool.Type.SQUEEZER -> R.string.squeezer_tool_name
                        SDMTool.Type.SWIPER -> R.string.swiper_tool_name
                    }
                    context.getString(toolNameId) to it.error!!.localized(context).asText().get(context)
                }
            "$errorMsg\n${errorTools.joinToString("\n") { "${it.first}: ${it.second}" }}"
        } else {
            context.getString(R.string.scheduler_notification_result_success_message)
        }
        setContentText(text)
        setStyle(NotificationCompat.BigTextStyle().bigText(text))
        log(TAG) { "getResultBuilder(): $results" }
    }

    private fun Set<Results>.toNotificationid(): Int {
        val baseId = (this.hashCode() and Int.MAX_VALUE) % 101
        return NOTIFICATION_ID_RANGE_RESULT + baseId
    }

    fun notifyResult(results: Set<Results>) {
        val id = results.toNotificationid()
        val notification = getResultBuilder(results).build()
        log(TAG) { "notifyResult($id, $results)" }
        notificationManager.notify(id, notification)
    }

    fun notifyError(scheduleId: ScheduleId) {
        val baseId = (scheduleId.hashCode() and Int.MAX_VALUE) % 101
        val id = NOTIFICATION_ID_RANGE_RESULT + baseId
        val notification = getBaseResultBuilder().apply {
            setContentTitle(context.getString(R.string.scheduler_notification_result_title))
            val text = context.getString(R.string.scheduler_notification_result_failure_message)
            setContentText(text)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }.build()
        log(TAG) { "notifyError($id, $scheduleId)" }
        notificationManager.notify(id, notification)
    }


    data class Results(
        val task: SDMTool.Task,
        val result: SDMTool.Task.Result? = null,
        val error: Exception? = null,
    )

    companion object {
        val TAG = logTag("Scheduler", "Notifications", "Worker")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.scheduler"
        internal const val NOTIFICATION_ID_RANGE_STATE = 1000
        internal const val NOTIFICATION_ID_RANGE_RESULT = 1200
    }
}

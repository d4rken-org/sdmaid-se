package eu.darken.sdmse.stats.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import eu.darken.sdmse.stats.core.forecast.StorageForecast
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.sdmse.common.ui.R as UiR

/**
 * The tap target of the low-space warning.
 *
 * [Intent.filterEquals] ignores extras, and both `TaskWorkerNotifications` and
 * `TaskResultNotifications` already hold request code `0` against a bare [MainActivity] intent.
 * Reusing that shape with `FLAG_UPDATE_CURRENT` would overwrite THEIR extras and route their taps
 * here, so this intent carries its own [action] and is posted under its own request code.
 */
internal fun lowSpaceContentIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
    action = LowSpaceNotifications.ACTION_LOW_SPACE
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    putExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION, ShortcutActivity.ACTION_OPEN_ANALYZER)
}

@Singleton
class LowSpaceNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    private val contentPendingIntent: PendingIntent

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stats_lowspace_notification_channel_label),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).run { notificationManager.createNotificationChannel(this) }

        contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            lowSpaceContentIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Posts the warning for [forecast].
     *
     * Returns an explicit [PostResult] instead of swallowing the outcome: only [PostResult.POSTED]
     * may spend the caller's transition latch, so a user who grants notification permission later
     * still gets warned while the storage is still filling.
     */
    fun notifyLowSpace(forecast: StorageForecast, freeBytes: Long): PostResult {
        if (!canPost()) {
            log(TAG, WARN) { "notifyLowSpace(): Blocked, notifications are not deliverable" }
            return PostResult.BLOCKED
        }

        val freeText = Formatter.formatShortFileSize(context, freeBytes)
        // Title and body are picked together: a trend title over an "out of space" body understates
        // the situation the body describes.
        val (title, body) = when (forecast) {
            is StorageForecast.Filling -> {
                val days = forecast.daysUntilFloor.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                val bodyText = context.getQuantityString2(
                    R.plurals.stats_lowspace_notification_forecast_body,
                    days,
                    days,
                    freeText,
                )
                context.getString(R.string.stats_lowspace_notification_title) to bodyText
            }

            else -> {
                val bodyText = context.getString(R.string.stats_lowspace_notification_low_body, freeText)
                context.getString(R.string.stats_lowspace_notification_low_title) to bodyText
            }
        }

        // Fresh builder per call: a shared builder would leak style/field state between posts.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setChannelId(CHANNEL_ID)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(UiR.drawable.ic_notification_mascot_24)
            .setAutoCancel(true)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(BigTextStyle().bigText(body))
            .build()

        return try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            log(TAG) { "notifyLowSpace($forecast, $freeBytes): Posted" }
            PostResult.POSTED
        } catch (e: Exception) {
            log(TAG, ERROR) { "notifyLowSpace() failed: ${e.asLog()}" }
            PostResult.FAILED
        }
    }

    fun cancel() {
        log(TAG) { "cancel()" }
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            log(TAG, ERROR) { "cancel() failed: ${e.asLog()}" }
        }
    }

    private fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            log(TAG, WARN) { "Notifications are disabled for the app" }
            return false
        }
        if (hasApiLevel(33) && !Permission.POST_NOTIFICATIONS.isGranted(context)) {
            log(TAG, WARN) { "POST_NOTIFICATIONS is not granted" }
            return false
        }
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            log(TAG, WARN) { "Channel $CHANNEL_ID is muted (IMPORTANCE_NONE)" }
            return false
        }
        return true
    }

    enum class PostResult {
        POSTED,
        BLOCKED,
        FAILED,
    }

    companion object {
        val TAG = logTag("Stats", "LowSpace", "Notifications")
        internal val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.stats.lowspace"
        internal val ACTION_LOW_SPACE = "${BuildConfigWrap.APPLICATION_ID}.ACTION_NOTIFICATION_LOW_SPACE"

        // Notification ID ranges in use: 1 (task worker), 75 (uninstall watcher),
        // 1000-1100 (scheduler state), 1200-1300 (scheduler result), 2000-2007 (task results).
        internal const val NOTIFICATION_ID = 3000

        // Distinct from every other PendingIntent request code in the app (all of which are 0),
        // so FLAG_UPDATE_CURRENT here can't rewrite another site's intent.
        internal const val REQUEST_CODE = 3000
    }
}

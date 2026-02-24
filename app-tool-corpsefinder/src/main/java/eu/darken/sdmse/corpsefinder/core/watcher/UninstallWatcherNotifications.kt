package eu.darken.sdmse.corpsefinder.core.watcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.notifications.PendingIntentCompat
import eu.darken.sdmse.corpsefinder.R
import javax.inject.Inject


class UninstallWatcherNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) : WatcherNotifications {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            CHANNEL_ID,
            context.getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_watcher_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).run { notificationManager.createNotificationChannel(this) }

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPi = openIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntentCompat.FLAG_IMMUTABLE
            )
        }

        builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setChannelId(CHANNEL_ID)
            setContentIntent(openPi)
            priority = NotificationCompat.PRIORITY_DEFAULT
            setSmallIcon(R.drawable.ic_notification_mascot_24)
            setContentTitle(context.getString(eu.darken.sdmse.common.R.string.app_name))
            setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
        }
    }

    private fun getBuilder(result: ExternalWatcherResult?): NotificationCompat.Builder {
        if (result == null) {
            return builder.apply {
                clearActions()
                setStyle(null)
                setContentTitle(context.getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_watcher_title))
                setContentText(context.getString(eu.darken.sdmse.common.R.string.general_progress_loading))
            }
        }

        return builder.apply {
            clearActions()
            setContentTitle(context.getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_watcher_title))
        }
    }

    private fun forDeletionResult(result: ExternalWatcherResult.Deletion): Notification = getBuilder(result).apply {
        val resultText = context.getQuantityString2(
            eu.darken.sdmse.corpsefinder.R.plurals.corpsefinder_watcher_notification_delete_result,
            result.deletedItems,
            result.appName?.get(context) ?: result.pkgId.name,
            Formatter.formatShortFileSize(context, result.freedSpace),
            result.deletedItems
        )
        setContentText(resultText)
        setStyle(BigTextStyle().bigText(resultText))
    }.build()

    private fun forScanResult(result: ExternalWatcherResult.Scan): Notification = getBuilder(result).apply {
        val resultText = context.getQuantityString2(
            eu.darken.sdmse.corpsefinder.R.plurals.corpsefinder_watcher_notification_scan_result,
            result.foundItems,
            result.foundItems,
            result.pkgId.name
        )
        setContentText(resultText)
        setStyle(BigTextStyle().bigText(resultText))

        if (result.foundItems > 0) {
            val deleteIntent = Intent(context, ExternalWatcherTaskReceiver::class.java).apply {
                action = ExternalWatcherTaskReceiver.TASK_INTENT
                val deleteTask = ExternalWatcherTask.Delete(
                    target = result.pkgId,
                )
                putExtra(ExternalWatcherTaskReceiver.EXTRA_TASK, deleteTask)
            }

            val deletePi = PendingIntent.getBroadcast(
                context,
                0,
                deleteIntent,
                PendingIntentCompat.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val deleteAction = NotificationCompat.Action(
                R.drawable.ic_delete,
                context.getString(eu.darken.sdmse.corpsefinder.R.string.corpsefinder_watcher_notification_delete_action),
                deletePi
            )
            addAction(deleteAction)
        }
    }.build()

    override fun notifyOfScan(result: ExternalWatcherResult.Scan) {
        log(TAG) { "notifyOfScan($result)" }
        val notification = forScanResult(result)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun notifyOfDeletion(result: ExternalWatcherResult.Deletion) {
        log(TAG) { "notifyOfDeletion($result)" }
        val notification = forDeletionResult(result)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun clearNotifications() {
        log(TAG) { "clearNotifications()" }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds abstract fun watcherNotifications(impl: UninstallWatcherNotifications): WatcherNotifications
    }

    companion object {
        val TAG = logTag("CorpseFinder", "Watcher", "Uninstall", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.corpsefinder.watcher.uninstall"
        internal const val NOTIFICATION_ID = 75
    }
}

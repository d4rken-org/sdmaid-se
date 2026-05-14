package eu.darken.sdmse.main.core.taskmanager

import android.app.NotificationManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a result notification when a TaskManager task finishes while the app is in the background,
 * and dismisses those notifications when the app returns to the foreground.
 *
 * Tasks that opt out via `TaskSubmitter.submit(notifyOnFinish = false)` are ignored (e.g. scheduler
 * runs, which post their own aggregate notification).
 *
 * "Background" here means `ProcessLifecycleOwner` reports the process as stopped — this has a small
 * built-in debounce, so a task that finishes within ~700ms of the user pressing Home may still be
 * treated as foreground and skipped. Acceptable for v1.
 */
@Singleton
class TaskResultNotifier @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val taskSubmitter: TaskSubmitter,
    private val resultNotifications: TaskResultNotifications,
    private val notificationManager: NotificationManager,
) {

    private val isStarted = AtomicBoolean(false)
    private val appForeground = AtomicBoolean(false)

    // Single lock guarding "decide-to-post" and "dismiss-all" so they cannot interleave.
    // Without it, a handle() that already saw appForeground=false could still post AFTER
    // ON_START flips foreground and runs dismissAll(), leaving a stale notification visible.
    private val notifyLock = Any()

    fun start() {
        if (!isStarted.compareAndSet(false, true)) {
            log(TAG, VERBOSE) { "start() called more than once; ignoring." }
            return
        }
        log(TAG) { "start()" }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        // Set foreground BEFORE dismissing so any handle() that wins the lock
                        // next will re-check and skip its post.
                        appForeground.set(true)
                        dismissAll()
                    }

                    Lifecycle.Event.ON_STOP -> appForeground.set(false)
                    else -> Unit
                }
            }
        )

        taskSubmitter.state
            .map { state -> state.tasks.associateBy { it.id } }
            .withPrevious()
            .onEach { (oldTasks, newTasks) ->
                if (oldTasks == null) {
                    // First emission: just seed the comparison baseline.
                    // Tasks already complete when the notifier starts are treated as historical
                    // (they would not survive process death in TaskManager anyway).
                    return@onEach
                }
                val newlyCompleted = newTasks.values.filter { task ->
                    task.isComplete && oldTasks[task.id]?.isComplete != true
                }
                newlyCompleted.forEach { task -> handle(task) }
            }
            .launchIn(appScope)
    }

    private fun handle(task: TaskSubmitter.ManagedTask) {
        if (!task.notifyOnFinish) {
            log(TAG, VERBOSE) { "Skip (opt-out): ${task.id}" }
            return
        }
        if (task.cancelledAt != null) {
            log(TAG, VERBOSE) { "Skip (cancelled): ${task.id}" }
            return
        }
        if (task.result == null && task.error == null) {
            log(TAG, VERBOSE) { "Skip (no outcome): ${task.id}" }
            return
        }

        synchronized(notifyLock) {
            // Re-check foreground inside the lock so a concurrent ON_START → dismissAll()
            // either runs entirely before we post (we then skip), or entirely after (it
            // cancels what we just posted).
            if (appForeground.get()) {
                log(TAG, VERBOSE) { "Skip (foreground): ${task.id}" }
                return
            }

            val id = TaskResultNotifications.notificationIdFor(task.toolType)
            try {
                val notification = resultNotifications.getNotification(task)
                notificationManager.notify(id, notification)
                log(TAG) { "Posted result notification id=$id for $task" }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to post result notification for ${task.id}: ${e.asLog()}" }
            }
        }
    }

    private fun dismissAll() {
        synchronized(notifyLock) {
            // Cancel by enumerating the full owned ID range — not a "what we posted this run"
            // set — so stale notifications from a prior process lifetime are also cleared
            // when the user next opens the app. Notifications survive process death; our
            // in-memory set wouldn't.
            log(TAG, VERBOSE) { "dismissAll()" }
            SDMTool.Type.entries.forEach { type ->
                val id = TaskResultNotifications.notificationIdFor(type)
                try {
                    notificationManager.cancel(id)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to cancel notification $id ($type): ${e.asLog()}" }
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("TaskManager", "Notifications", "ResultNotifier")
    }
}

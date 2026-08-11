package eu.darken.sdmse.common.debug.exit

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs why the previous process instances died (crash, ANR, low memory, ...). A process that was
 * killed can't log its own death, so this is the only source for it in a debug log.
 */
@Singleton
class ExitInfoLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val cacheLock = Mutex()
    private var cachedRecords: List<String>? = null

    /**
     * Logs the previous exit reasons. Idempotent and callable multiple times: the records are
     * fetched once per process, so a repeat emission (e.g. into a recording started later) is
     * identical to the first.
     */
    fun logPreviousExits() {
        appScope.launch(dispatcherProvider.IO) {
            val records = cacheLock.withLock {
                cachedRecords ?: fetchRecords().also { cachedRecords = it }
            }
            records.forEach { record -> log(TAG, INFO) { record } }
        }
    }

    private fun fetchRecords(): List<String> {
        if (!hasApiLevel(30)) return emptyList()
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager
                .getHistoricalProcessExitReasons(null, 0, MAX_RECORDS)
                .map { it.describe() }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to read historical process exit reasons: ${e.asLog()}" }
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.describe(): String = listOf(
        "Previous exit: ${Instant.ofEpochMilli(timestamp)}",
        "process=$processName",
        "reason=${exitReasonLabel(reason)}($reason)",
        "status=$status",
        "importance=$importance",
        "pss=${pss}kB",
        "rss=${rss}kB",
        "description=$description",
    ).joinToString(" ")

    companion object {
        private val TAG = logTag("Debug", "ExitInfo")
        private const val MAX_RECORDS = 5
    }
}

/**
 * Maps an [ApplicationExitInfo] reason code to its constant name. The codes are spelled out as
 * literals so the mapping stays pure (and testable on the JVM) and covers reasons that were added
 * in later API levels than the one we compile against.
 */
internal fun exitReasonLabel(reason: Int): String = EXIT_REASONS[reason] ?: "UNKNOWN($reason)"

private val EXIT_REASONS = mapOf(
    0 to "REASON_UNKNOWN",
    1 to "REASON_EXIT_SELF",
    2 to "REASON_SIGNALED",
    3 to "REASON_LOW_MEMORY",
    4 to "REASON_CRASH",
    5 to "REASON_CRASH_NATIVE",
    6 to "REASON_ANR",
    7 to "REASON_INITIALIZATION_FAILURE",
    8 to "REASON_PERMISSION_CHANGE",
    9 to "REASON_EXCESSIVE_RESOURCE_USAGE",
    10 to "REASON_USER_REQUESTED",
    11 to "REASON_USER_STOPPED",
    12 to "REASON_DEPENDENCY_DIED",
    13 to "REASON_OTHER",
    14 to "REASON_FREEZER",
    15 to "REASON_PACKAGE_STATE_CHANGE",
    16 to "REASON_PACKAGE_UPDATED",
)

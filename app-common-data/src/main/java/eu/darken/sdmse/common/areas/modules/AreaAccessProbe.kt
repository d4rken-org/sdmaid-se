package eu.darken.sdmse.common.areas.modules

import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Checks write access by creating [probe] and removing it again.
 *
 * Cleanup runs whenever creation was attempted, also if the caller is cancelled midway, and ignores dry-run.
 * Only pass freshly generated, unique paths: [probe] may be deleted even when [create] failed.
 * [cleanupTimeout] is cooperative, it can't interrupt a blocking filesystem or provider call.
 */
internal suspend fun probeWriteAccess(
    tag: String,
    probe: APath,
    create: suspend () -> Unit,
    exists: suspend () -> Boolean,
    delete: suspend () -> Unit,
    cleanupTimeout: Duration = PROBE_CLEANUP_TIMEOUT,
): Boolean {
    var createAttempted = false
    var createFailed = false

    val accessible = try {
        if (exists()) {
            log(tag, WARN) { "Probe exists already, not touching it: $probe" }
            false
        } else {
            createAttempted = true
            try {
                create()
            } catch (e: IOException) {
                createFailed = true
                throw e
            }
            exists().also {
                if (!it) log(tag) { "Failed to create probe $probe" }
            }
        }
    } catch (e: IOException) {
        log(tag, WARN) { "Couldn't create probe $probe: ${e.asLog()}" }
        false
    } finally {
        if (createAttempted) {
            withContext(NonCancellable) {
                try {
                    withTimeout(cleanupTimeout) { delete() }
                } catch (e: Exception) {
                    // When creation failed there is usually nothing to delete
                    log(tag, if (createFailed) WARN else ERROR) { "Clean up of $probe failed: $e" }
                }
            }
        }
    }

    // An IOException can be a wrapped cancellation, don't report "inaccessible" for a cancelled caller
    currentCoroutineContext().ensureActive()
    return accessible
}

internal suspend fun LocalGateway.probeWriteAccess(
    tag: String,
    probe: LocalPath,
    mode: LocalGateway.Mode,
): Boolean = probeWriteAccess(
    tag = tag,
    probe = probe,
    create = { createFile(probe, mode = mode) },
    exists = { exists(probe, mode = mode) },
    delete = { delete(probe, mode = mode, dryRun = false) },
)

internal suspend fun SAFGateway.probeWriteAccess(
    tag: String,
    probe: SAFPath,
): Boolean = probeWriteAccess(
    tag = tag,
    probe = probe,
    create = { createFile(probe) },
    exists = { exists(probe) },
    delete = { delete(probe, recursive = false, dryRun = false) },
)

internal val PROBE_CLEANUP_TIMEOUT = 10.seconds

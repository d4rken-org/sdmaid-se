package eu.darken.sdmse.common.backup

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates config backup/restore with the rest of the app — most importantly tool task execution,
 * which reads and writes the same databases and settings a restore rewrites.
 *
 * Semantics:
 * - [runExclusive] (backup/restore): refuses immediately with [BackupBusyException] if any shared
 *   work is running or another backup operation is active. Backup operations never *wait* for
 *   tasks, which keeps the waiting one-directional and deadlock-free.
 * - [runShared] (tool tasks): shared work runs concurrently with other shared work, but waits until
 *   an active backup operation has finished before starting.
 *
 * All state transitions happen under a single [Mutex] which is never held across the guarded work,
 * so there is no check-then-act race between the two directions.
 */
@Singleton
class BackupOperationGate @Inject constructor() {

    private val stateLock = Mutex()
    private val exclusiveActive = MutableStateFlow(false)
    private var sharedActive = 0

    /** Runs a backup/restore operation, or throws [BackupBusyException] if anything else is active. */
    suspend fun <T> runExclusive(block: suspend () -> T): T {
        stateLock.withLock {
            if (exclusiveActive.value || sharedActive > 0) throw BackupBusyException()
            exclusiveActive.value = true
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                stateLock.withLock { exclusiveActive.value = false }
            }
        }
    }

    /** Runs tool work, waiting for an active backup operation to finish first. */
    suspend fun <T> runShared(block: suspend () -> T): T {
        while (true) {
            val acquired = stateLock.withLock {
                if (exclusiveActive.value) {
                    false
                } else {
                    sharedActive++
                    true
                }
            }
            if (acquired) break
            exclusiveActive.first { !it }
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                stateLock.withLock { sharedActive-- }
            }
        }
    }
}

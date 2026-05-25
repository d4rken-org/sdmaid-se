package eu.darken.sdmse.automation.core

import android.content.ComponentName
import android.provider.Settings
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.shell.ShellOps

/**
 * Orchestrates enabling our [AutomationService] and waiting for it to actually bind.
 *
 * The decision logic lives here (behind the [Io] seam) so it can be unit-tested without the heavy
 * [AutomationManager] dependency graph. [AutomationManager] provides the real [Io] implementation.
 *
 * Two ROM failure modes are handled (both observed on WAIPU TV / Android 14):
 * - **Mode A**: an app-initiated write to `enabled_accessibility_services` is silently reverted (the
 *   value never persists, sometimes wiping unrelated third-party services too).
 * - **Mode B**: the value persists but the system never binds the service.
 *
 * Both surface as "the service didn't come up", so the privileged-shell fallback is triggered by the
 * bind result, not merely by whether the value persisted. At most one direct + one shell attempt.
 */
class AcsActivator(
    private val io: Io,
    private val bindTimeoutMs: Long = DEFAULT_BIND_TIMEOUT_MS,
) {

    interface Io {
        suspend fun shellMode(): ShellOps.Mode?
        suspend fun isAvoidDirectWrite(): Boolean
        suspend fun markDirectWriteUnreliable()

        /** Writes the list via our own process and returns the post-write readback. */
        suspend fun writeDirect(services: Set<ComponentName>): Set<ComponentName>

        /** Writes the list via the privileged shell. Returns whether the intended list persisted. */
        suspend fun writeShell(services: Set<ComponentName>, mode: ShellOps.Mode): Boolean

        /** Waits up to [timeoutMs] for the service to bind. Returns the handle, or null on timeout. */
        suspend fun awaitBound(timeoutMs: Long): AutomationServiceHandle?
    }

    /**
     * @param intended the full desired service list (incl. any third-party services that were already
     * enabled), so a Mode A wipe is repaired rather than dropping other apps' services.
     */
    suspend fun enable(intended: Set<ComponentName>): AutomationServiceHandle? {
        val mode = io.shellMode()

        if (io.isAvoidDirectWrite()) {
            log(TAG, WARN) { "enable(): Direct write known-unreliable for this build, using shell directly" }
            if (mode == null) {
                log(TAG, WARN) { "enable(): No privileged shell available, cannot enable" }
                return null
            }
            return if (io.writeShell(intended, mode)) io.awaitBound(bindTimeoutMs) else null
        }

        val afterDirect = io.writeDirect(intended)
        if (!writeMatchesIntent(intent = intended, actual = afterDirect)) {
            // Mode A: the write was reverted (often wiping unrelated third-party services too).
            log(TAG, WARN) { "enable(): Direct write was reverted (intended=$intended, actual=$afterDirect)" }
            io.markDirectWriteUnreliable()
            if (mode == null) return null
            return if (io.writeShell(intended, mode)) io.awaitBound(bindTimeoutMs) else null
        }

        io.awaitBound(bindTimeoutMs)?.let { return it }

        // Mode B: the value persisted but the service never bound. Try the shell once.
        log(TAG, WARN) { "enable(): Direct write persisted but service didn't bind, trying shell" }
        if (mode == null) return null
        if (!io.writeShell(intended, mode)) return null
        return io.awaitBound(bindTimeoutMs)?.also {
            // Only now is it proven that the shell path was necessary on this build.
            io.markDirectWriteUnreliable()
        }
    }

    /** How a service-list write should be performed (used for disable / re-toggle). */
    enum class WriteStrategy { DIRECT, SHELL, SKIP }

    companion object {
        private val TAG = logTag("Automation", "AcsActivator")

        private const val DEFAULT_BIND_TIMEOUT_MS = 10 * 1000L

        /**
         * On a build flagged as direct-write-unreliable we must NEVER write via our own process (it can
         * silently wipe unrelated third-party services). So when no privileged shell is available there,
         * the only safe option is to [WriteStrategy.SKIP] the write rather than fall back to a direct one.
         */
        internal fun writeStrategy(avoidDirectWrite: Boolean, hasShell: Boolean): WriteStrategy = when {
            !avoidDirectWrite -> WriteStrategy.DIRECT
            hasShell -> WriteStrategy.SHELL
            else -> WriteStrategy.SKIP
        }

        /** A write achieves the intent when every intended component is present (extras are fine). */
        internal fun writeMatchesIntent(intent: Set<ComponentName>, actual: Set<ComponentName>): Boolean =
            if (intent.isEmpty()) actual.isEmpty() else actual.containsAll(intent)

        internal fun flattenServices(services: Set<ComponentName>): String =
            services.joinToString(":") { it.flattenToString() }

        /**
         * Builds the `settings put secure enabled_accessibility_services …` command. The value is
         * single-quoted so the interactive shell doesn't expand `$` (legal in nested-class component
         * names of preserved third-party services). Component strings can't contain `'`, so single
         * quoting needs no further escaping.
         */
        internal fun enabledServicesPutCmd(value: String): String =
            "settings put secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES} '$value'"
    }
}

package eu.darken.sdmse.automation.core.specs

import android.os.SystemClock
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.errors.AutomationInterferenceException
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

/**
 * Detects when a foreign app (e.g. an app-locker like Avast App Lock) is holding the active
 * window instead of the system settings screen we are trying to automate. Without this, such
 * interference only surfaces as a generic timeout/compatibility error after minutes of retries.
 *
 * One instance lives for the lifetime of a single window-check (i.e. one step), so its sightings
 * accumulate across the stepper's inner retry loop. Benign windows reset the tracker.
 *
 * Two confidence levels:
 * - [knownBlockers]: high confidence, abort on the first sighting (even if it is also the target).
 * - generic: abort only once the same non-system foreign app has dominated the window for
 *   [PERSIST_THRESHOLD_MS], so transient windows during the settings launch don't trip it.
 */
class SettingsInterferenceDetector(
    private val expectedPkgs: Set<Pkg.Id>,
    private val targetPkg: Pkg.Id,
    private val knownBlockers: Set<Pkg.Id> = KNOWN_INTERFERENCE_PKGS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val resolveLabel: suspend (Pkg.Id) -> String?,
    private val isSystemApp: suspend (Pkg.Id) -> Boolean,
) {

    private var currentForeign: Pkg.Id? = null
    private var firstSeenAt: Long = 0L
    private val systemAppCache = mutableMapOf<Pkg.Id, Boolean>()

    /**
     * Inspect the current window [root]. Throws [AutomationInterferenceException] once we are
     * confident a foreign app is blocking the settings screen. Safe to call on every observed
     * root: a matching/benign window just resets the persistence tracker.
     */
    suspend fun evaluate(root: ACSNodeInfo, ownPkg: String) {
        // ACSNodeInfo.pkgId is non-null and maps a missing name to Pkg.Id("null"), so extract manually.
        val rootPkg = root.packageName?.toString()?.takeIf { it.isNotBlank() }?.toPkgId()
        if (rootPkg == null) {
            // Unknown/transient window - don't reset, just wait for the next observation.
            return
        }

        // The settings screen (or our own overlay) is up - any prior foreign sighting is stale.
        if (rootPkg in expectedPkgs || rootPkg.name == ownPkg) {
            reset()
            return
        }

        // Known blockers are high-confidence: fire immediately, even when it is also the target app.
        if (rootPkg in knownBlockers) {
            log(TAG, WARN) { "Known interfering app is holding the window: $rootPkg" }
            throw AutomationInterferenceException(rootPkg, resolveLabel(rootPkg))
        }

        // The app currently being cleaned may briefly own the window - that's expected.
        if (rootPkg == targetPkg) {
            reset()
            return
        }

        // System apps (launcher, SystemUI, permission controller, ...) are benign.
        val system = systemAppCache[rootPkg] ?: isSystemApp(rootPkg).also { systemAppCache[rootPkg] = it }
        if (system) {
            reset()
            return
        }

        // Generic detection: require the same foreign app to dominate the window for a while.
        val now = nowMs()
        if (rootPkg != currentForeign) {
            currentForeign = rootPkg
            firstSeenAt = now
            return
        }
        if (now - firstSeenAt >= PERSIST_THRESHOLD_MS) {
            log(TAG, WARN) { "Foreign app is persistently blocking the window: $rootPkg" }
            throw AutomationInterferenceException(rootPkg, resolveLabel(rootPkg))
        }
    }

    private fun reset() {
        currentForeign = null
        firstSeenAt = 0L
    }

    companion object {
        private val TAG = logTag("Automation", "InterferenceDetector")

        private const val PERSIST_THRESHOLD_MS = 1500L

        /**
         * Apps known to lock or overlay the system settings screen. The generic detector covers
         * unlisted ones too; this list mainly lets us abort instantly with higher confidence.
         */
        val KNOWN_INTERFERENCE_PKGS: Set<Pkg.Id> = setOf(
            "com.avast.android.mobilesecurity", // Avast Mobile Security (App Lock) - originally reported
            "com.antivirus", // AVG AntiVirus (App Lock)
            "com.symantec.mobilesecurity", // Norton 360 / Mobile Security
            "com.symantec.applock", // Norton App Lock
            "com.wsandroid.suite", // McAfee Security
            "com.kms.free", // Kaspersky Mobile / Internet Security
            "com.bitdefender.security", // Bitdefender Mobile Security
            "com.domobile.applockwatcher", // AppLock (DoMobile)
            "com.domobile.applock", // AppLock (DoMobile, legacy)
            "com.sp.protector.free", // Smart AppLock
        ).map { it.toPkgId() }.toSet()
    }
}

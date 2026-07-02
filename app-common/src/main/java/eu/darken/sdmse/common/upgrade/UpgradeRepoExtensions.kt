package eu.darken.sdmse.common.upgrade

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TAG = logTag("Upgrade", "Repo", "Extensions")

suspend fun UpgradeRepo.isPro(): Boolean = upgradeInfo.first().isPro

/**
 * Pro check for backend safety-net gates (e.g. a tool's task-submit boundary).
 *
 * Deliberately generous — it prefers to fail open rather than block a paying user:
 * - Returns `true` immediately if we already know the user is Pro (active purchase or grace period).
 * - Otherwise nudges a billing [UpgradeRepo.refresh] and waits up to [timeout] for a Pro state to
 *   appear. This rescues a genuine Pro user from the GPlay cold-start race, where [UpgradeRepo.upgradeInfo]
 *   reports non-Pro until the billing connection settles.
 * - Only denies when billing settles within the window and still reports no purchase (the realistic
 *   "free user reached a Pro-only path via a UI mistake" case, where billing is already connected).
 * - On any error, fails open (returns `true`) — a billing hiccup must never block a paying user.
 *
 * The FOSS flavor reads from a synchronous cache, so the fast path resolves immediately there.
 */
suspend fun UpgradeRepo.isProSettled(timeout: Duration = 5.seconds): Boolean = try {
    if (upgradeInfo.first().isPro) {
        true
    } else {
        refresh()
        withTimeoutOrNull(timeout) { upgradeInfo.firstOrNull { it.isPro } != null } == true
    }
} catch (e: Exception) {
    log(TAG, WARN) { "isProSettled() failed, failing open (allowing): ${e.asLog()}" }
    true
}

/**
 * Pro check for UI gates: tap handlers that route between a gated action and the upgrade screen.
 *
 * Resolves immediately in the common cases — already Pro, or billing settled and not Pro — so the
 * upgrade screen stays snappy for free users (unlike [isProSettled], whose non-Pro path always
 * waits out its timeout). Only while billing is still connecting (GPlay cold start or reconnect,
 * where [UpgradeRepo.upgradeInfo] reports non-Pro even for paying users) does it wait, up to
 * [timeout], for the first real billing result — so a Pro user isn't bounced to the upgrade
 * screen by the handshake race. On timeout or error it falls back to the current state: the UI
 * route is recoverable, and the backend [isProSettled] gate remains the enforcement safety net.
 */
suspend fun UpgradeRepo.isProForUi(timeout: Duration = 3.seconds): Boolean = try {
    when {
        upgradeInfo.first().isPro -> true
        isSettled.first() -> false
        else -> {
            withTimeoutOrNull(timeout) { isSettled.first { settled -> settled } }
            upgradeInfo.first().isPro
        }
    }
} catch (e: Exception) {
    log(TAG, WARN) { "isProForUi() failed, failing open (allowing): ${e.asLog()}" }
    true
}

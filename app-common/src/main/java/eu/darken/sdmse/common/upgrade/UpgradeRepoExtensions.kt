package eu.darken.sdmse.common.upgrade

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
 * - Otherwise nudges a billing [UpgradeRepo.refresh] and waits for a Pro state to appear. This
 *   rescues a genuine Pro user from the GPlay cold-start race, where [UpgradeRepo.upgradeInfo]
 *   reports non-Pro until the billing connection settles. [timeout] is the budget for the WHOLE
 *   reconciliation (refresh round-trip plus wait), so a Play call that hangs can't stretch the gate
 *   past it.
 * - Only denies when the state after that window is settled, error-free and still reports no
 *   purchase (the realistic "free user reached a Pro-only path via a UI mistake" case, where billing
 *   is already connected).
 * - Fails open (returns `true`) when the window elapses without billing settling, when the settled
 *   state carries an error, and on any exception — a billing hiccup must never block a paying user.
 *
 * The FOSS flavor reads from a synchronous cache, so the fast path resolves immediately there.
 */
suspend fun UpgradeRepo.isProSettled(timeout: Duration = 5.seconds): Boolean = try {
    if (upgradeInfo.first().isPro) {
        true
    } else {
        // One budget for the WHOLE reconciliation: refresh + wait. Waiting only for isPro (not
        // isSettled) is deliberate — replayed emissions are already settled, so a settled-predicate
        // would return the stale pre-refresh state instead of the refresh outcome.
        val proAppeared = withTimeoutOrNull(timeout) {
            refresh()
            upgradeInfo.first { it.isPro }
            true
        } == true
        if (proAppeared) {
            true
        } else {
            // Full window elapsed after the refresh started — the pipeline has caught up by now.
            val current = upgradeInfo.first()
            when {
                current.isPro -> true           // pro landed as the window closed: never deny a known pro
                current.error != null -> true   // settled error state: fail open
                current.isSettled -> false      // settled and still no purchase: the documented deny
                else -> true                    // never settled within the budget: documented fail-open
            }
        }
    }
} catch (e: CancellationException) {
    // A cancelled caller must not continue down the Pro path via the fail-open below.
    throw e
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
 * [timeout], for the first settled Info — so a Pro user isn't bounced to the upgrade screen by
 * the handshake race. Every decision reads isPro and isSettled off the SAME Info emission, so a
 * settle signal can never pair with stale ownership. On timeout or error it falls back to the
 * current state: the UI route is recoverable, and the backend [isProSettled] gate remains the
 * enforcement safety net.
 */
suspend fun UpgradeRepo.isProForUi(timeout: Duration = 3.seconds): Boolean = try {
    val current = upgradeInfo.first()
    when {
        current.isPro -> true
        current.isSettled -> false
        else -> {
            val settled = withTimeoutOrNull(timeout) { upgradeInfo.first { it.isSettled } }
            (settled ?: upgradeInfo.first()).isPro
        }
    }
} catch (e: CancellationException) {
    // A cancelled caller must not continue down the Pro path via the fail-open below.
    throw e
} catch (e: Exception) {
    log(TAG, WARN) { "isProForUi() failed, failing open (allowing): ${e.asLog()}" }
    true
}

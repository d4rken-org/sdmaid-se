package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.UpgradeDiagnostics
import eu.darken.sdmse.main.core.CurriculumVitae
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports the local billing cache and the lifetime Pro-state history into the debug log header.
 *
 * `lastProStateAt > 0` is the "this install once confirmed a real Pro purchase" bit. It predates
 * the CurriculumVitae pro-state counters by years and lives in a DataStore that was never migrated
 * or renamed, so it survives update chains that the newer counters can't speak to. Without it in
 * the header, a purchase complaint can't be told apart from a never-bought install.
 *
 * Depends on [BillingCache] and [CurriculumVitae] alone -- see [UpgradeDiagnostics] for why this
 * must not pull in UpgradeRepoGplay.
 */
@Singleton
class UpgradeDiagnosticsGplay @Inject constructor(
    private val billingCache: BillingCache,
    private val curriculumVitae: CurriculumVitae,
) : UpgradeDiagnostics {

    // Test seam: the bounded read below runs on real dispatchers, so a virtual-time test cannot
    // advance the production bound. Same pattern as BillingCache.cacheTimeoutMs.
    internal var historyTimeoutMs: Long = HISTORY_TIMEOUT_MS

    override suspend fun debugInfo(): String {
        val cache = try {
            val snapshot = billingCache.snapshot()
            val lastProAt = snapshot.lastProStateAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) } ?: "never"
            val lastProSku = snapshot.lastProStateSku.takeIf { it.isNotEmpty() } ?: "unknown/legacy"
            val unconfirmedSince =
                snapshot.proUnconfirmedSince.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) } ?: "none"
            "BillingCache(lastProStateAt=$lastProAt, lastProStateSku=$lastProSku, " +
                "proUnconfirmedSince=$unconfirmedSince)"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Billing cache unavailable: ${e.asLog()}" }
            "BillingCache=unavailable"
        }
        // Separate boundary from the cache read above on purpose: these are different DataStores,
        // and the counters only cover installs new enough to have them. A failure to read one must
        // not suppress the other's independent evidence.
        val history = try {
            // Bounded like the cache read above: a wedged DataStore file lock would otherwise hold
            // the debug-log header - and with it the start of the recording - forever.
            withTimeoutOrNull(historyTimeoutMs) { curriculumVitae.proHistory().toString() } ?: run {
                log(TAG, WARN) { "Pro history timed out after ${historyTimeoutMs}ms" }
                "unavailable"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Pro history unavailable: ${e.asLog()}" }
            "unavailable"
        }
        return "$cache, ProHistory=$history"
    }

    companion object {
        private const val HISTORY_TIMEOUT_MS = 2_000L
        private val TAG = logTag("Upgrade", "Gplay", "Diagnostics")
    }
}

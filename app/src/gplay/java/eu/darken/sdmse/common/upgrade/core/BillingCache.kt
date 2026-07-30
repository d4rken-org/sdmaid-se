package eu.darken.sdmse.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.basicReader
import eu.darken.sdmse.common.datastore.basicWriter
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.billingCacheDataStore by preferencesDataStore(name = "settings_gplay")

@Singleton
class BillingCache internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.billingCacheDataStore)

    // Test seam: the bounded reads/writes below run on real dispatchers, so a virtual-time test
    // cannot advance the production bound. Same pattern as UpgradeRepoGplay.launchTimeoutMs.
    internal var cacheTimeoutMs: Long = CACHE_TIMEOUT_MS

    // Raw keys shared between the DataStoreValues and stampLastProState's transaction — one
    // source of truth for key name and encoding.
    private val lastProStateAtKey = longPreferencesKey("gplay.cache.lastProAt")
    private val lastProStateSkuKey = stringPreferencesKey("gplay.cache.lastProSku")
    private val proUnconfirmedSinceKey = longPreferencesKey("gplay.cache.proUnconfirmedAt")

    val lastProStateAt = dataStore.createValue(
        key = lastProStateAtKey,
        reader = basicReader(0L),
        writer = basicWriter(),
    )
    val lastProStateSku = dataStore.createValue(
        key = lastProStateSkuKey,
        reader = basicReader(""),
        writer = basicWriter(),
    )

    // Start of the current "fresh data can't confirm Pro" episode (0 = none/confirmed). Drives the
    // delayed grace hint on the upgrade screen; stamped only from fresh billing reconciliations —
    // see UpgradeRepoGplay.recordProUnconfirmed().
    val proUnconfirmedSince = dataStore.createValue(
        key = proUnconfirmedSinceKey,
        reader = basicReader(0L),
        writer = basicWriter(),
    )

    // Point-in-time view of all three values. Reading them via three separate .value() calls can
    // straddle a concurrent stampLastProState() and observe a combination that never existed --
    // that write is transactional precisely because the values are only meaningful together.
    data class Snapshot(
        val lastProStateAt: Long,
        val lastProStateSku: String,
        val proUnconfirmedSince: Long,
    )

    // Bounded on purpose: a wedged DataStore file lock would otherwise hang the caller forever.
    // A timeout must NOT fall back to the default snapshot -- that would report "never bought"
    // for an install whose evidence merely couldn't be read, which is the exact distinction the
    // debug-log header exists to make.
    suspend fun snapshot(): Snapshot {
        val prefs = withTimeoutOrNull(cacheTimeoutMs) { dataStore.data.first() } ?: run {
            log(TAG, WARN) { "snapshot() timed out after ${cacheTimeoutMs}ms" }
            throw IOException("BillingCache snapshot timed out after ${cacheTimeoutMs}ms")
        }
        return Snapshot(
            lastProStateAt = prefs[lastProStateAtKey] ?: 0L,
            lastProStateSku = prefs[lastProStateSkuKey] ?: "",
            proUnconfirmedSince = prefs[proUnconfirmedSinceKey] ?: 0L,
        )
    }

    // One transaction for all three values: the timestamp gates the grace period, the SKU modifies
    // its window length, and a confirmation closes the unconfirmed episode — none of it may be
    // observable half-updated. `at` is the confirmation's OCCURRENCE time (commit time of the Play
    // round-trip). The episode is closed only if it began at or before `at`: a failure that occurred
    // AFTER this confirmation (e.g. a connection drop right after this success, delivered to the
    // entitlement layer out of order) opened a still-valid episode that this older confirmation must
    // not erase.
    suspend fun stampLastProState(skuId: String, at: Long) {
        // Fail-soft: this decorates the entitlement path, it must never be the thing that blocks it.
        // A wedged file lock (timeout) and a broken write (IOException, corrupt file, no disk space)
        // are the same to the caller — the stamp is lost, the bookkeeping around it carries on.
        try {
            withTimeoutOrNull(cacheTimeoutMs) {
                dataStore.edit { prefs ->
                    prefs[lastProStateSkuKey] = skuId
                    prefs[lastProStateAtKey] = at
                    val episodeStart = prefs[proUnconfirmedSinceKey] ?: 0L
                    if (episodeStart in 1..at) prefs[proUnconfirmedSinceKey] = 0L
                }
            } ?: log(TAG, WARN) { "stampLastProState($skuId, $at) timed out after ${cacheTimeoutMs}ms, write skipped" }
        } catch (e: CancellationException) {
            // Caught before the general case on purpose: our caller going away is not a write
            // failure, and swallowing it would break their structured concurrency.
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "stampLastProState($skuId, $at) failed, write skipped: ${e.asLog()}" }
        }
    }

    companion object {
        private const val CACHE_TIMEOUT_MS = 2_000L
        private val TAG = logTag("Upgrade", "Gplay", "BillingCache")
    }
}

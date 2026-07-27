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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_gplay")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

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

    suspend fun snapshot(): Snapshot {
        val prefs = dataStore.data.first()
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
        dataStore.edit { prefs ->
            prefs[lastProStateSkuKey] = skuId
            prefs[lastProStateAtKey] = at
            val episodeStart = prefs[proUnconfirmedSinceKey] ?: 0L
            if (episodeStart in 1..at) prefs[proUnconfirmedSinceKey] = 0L
        }
    }
}

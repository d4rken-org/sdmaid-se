package eu.darken.sdmse.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_gplay")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val lastProStateAt = dataStore.createValue("gplay.cache.lastProAt", 0L)
    val lastProStateSku = dataStore.createValue("gplay.cache.lastProSku", "")

    // Start of the current "fresh data can't confirm Pro" episode (0 = none/confirmed). Drives the
    // delayed grace hint on the upgrade screen; stamped only from fresh billing reconciliations —
    // see UpgradeRepoGplay.recordProUnconfirmed().
    val proUnconfirmedSince = dataStore.createValue("gplay.cache.proUnconfirmedAt", 0L)
}

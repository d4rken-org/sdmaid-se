package eu.darken.sdmse.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fossCacheDataStore by preferencesDataStore(name = "settings_foss")

@Singleton
class FossCache internal constructor(
    // Test seam: the store is handed in so a test can supply its own DataStore instead of the
    // Context-bound production delegate. Same pattern as BillingCache.
    private val dataStore: DataStore<Preferences>,
    json: Json,
) {

    @Inject constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(context.fossCacheDataStore, json)

    val upgrade = dataStore.createValue<FossUpgrade?>(
        key = "foss.upgrade",
        json = json,
        defaultValue = null,
    )
}

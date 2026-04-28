package eu.darken.sdmse.exclusion.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_exclusion")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val removedDefaultExclusions = dataStore.createValue("exclusion.default.removed", emptySet<String>(), json)

    companion object {
        internal val TAG = logTag("Exclusion", "Settings")
    }
}
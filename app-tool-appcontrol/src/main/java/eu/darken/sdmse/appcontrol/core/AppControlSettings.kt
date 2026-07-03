package eu.darken.sdmse.appcontrol.core

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
class AppControlSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_appcontrol")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val listSort = dataStore.createValue("list.sort.settings", SortSettings(), json)
    val listFilter = dataStore.createValue("list.filter.settings", FilterSettings(), json)
    val ackSizeSortCaveat = dataStore.createValue("list.filter.sizesort.caveat.ack", false)
    val moduleSizingEnabled = dataStore.createValue("module.sizing.enabled", true)
    val moduleActivityEnabled = dataStore.createValue("module.activity.enabled", true)
    val includeMultiUserEnabled = dataStore.createValue("include.multiuser.enabled", false)

    companion object {
        internal val TAG = logTag("AppControl", "Settings")
    }
}
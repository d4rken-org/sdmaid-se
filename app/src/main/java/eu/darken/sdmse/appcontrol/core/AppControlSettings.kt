package eu.darken.sdmse.appcontrol.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AppControlSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_appcontrol")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val listSort = dataStore.createValue("list.sort.settings", SortSettings(), moshi)
    val listFilter = dataStore.createValue("list.filter.settings", FilterSettings(), moshi)
    val ackSizeSortCaveat = dataStore.createValue("list.filter.sizesort.caveat.ack", false)
    val moduleSizingEnabled = dataStore.createValue("module.sizing.enabled", true)
    val moduleActivityEnabled = dataStore.createValue("module.activity.enabled", true)

    override val mapper = PreferenceStoreMapper(
        moduleSizingEnabled,
        moduleActivityEnabled
    )

    companion object {
        internal val TAG = logTag("AppControl", "Settings")
    }
}
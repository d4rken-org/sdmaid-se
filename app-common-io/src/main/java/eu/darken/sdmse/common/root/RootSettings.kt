package eu.darken.sdmse.common.root

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_root")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val useRoot = dataStore.createValue("core.root.enabled", null as Boolean?)

    override val mapper = PreferenceStoreMapper(
        useRoot
    )

    companion object {
        internal val TAG = logTag("Root", "Settings")
    }
}
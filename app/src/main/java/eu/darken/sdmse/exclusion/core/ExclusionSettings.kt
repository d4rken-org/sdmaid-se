package eu.darken.sdmse.exclusion.core

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
class ExclusionSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_exclusion")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val removedDefaultExclusions = dataStore.createValue("exclusion.default.removed", emptySet<String>(), moshi)

    override val mapper = PreferenceStoreMapper(

    )

    companion object {
        internal val TAG = logTag("Exclusion", "Settings")
    }
}
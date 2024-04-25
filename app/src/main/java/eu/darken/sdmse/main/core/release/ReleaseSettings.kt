package eu.darken.sdmse.main.core.release

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
class ReleaseSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_release")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val didReleasePartyCheck = dataStore.createValue("release.party.checked", false)
    val wantsBeta = dataStore.createValue("release.prerelease.consent", false)
    val earlyAdopter = dataStore.createValue("release.earlyadopter", null as Boolean?)

    override val mapper = PreferenceStoreMapper(

    )

    companion object {
        internal val TAG = logTag("Release", "Settings")
    }
}
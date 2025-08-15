package eu.darken.sdmse.main.core.release

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
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_release")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // Was used during v1.0 migration
    // See https://github.com/d4rken-org/sdmaid-se/blob/f52df69cb6c54b39775908952e7065796a1a5087/app/src/main/java/eu/darken/sdmse/main/core/release/ReleaseManager.kt#L38-L67
    val releasePartyAt = dataStore.createValue("release.party.date", null as Instant?, moshi)
    val wantsBeta = dataStore.createValue("release.prerelease.consent", false)
    val earlyAdopter = dataStore.createValue("release.earlyadopter", null as Boolean?)

    override val mapper = PreferenceStoreMapper(

    )

    companion object {
        internal val TAG = logTag("Release", "Settings")
    }
}
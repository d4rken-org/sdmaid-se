package eu.darken.sdmse.main.core.motd

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotdSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_motd")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val lastMotd = dataStore.createValue<MotdState?>("motd.state.cache", null, moshi)
    val lastDismissedMotd = dataStore.createValue<UUID?>("motd.last.dismissed", null, moshi)
    val isMotdEnabled = dataStore.createValue("motd.enabled", true)


    companion object {
        internal val TAG = logTag("Motd", "Settings")
    }
}
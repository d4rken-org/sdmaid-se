package eu.darken.sdmse.appcleaner.core

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
class AppCleanerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_appcleaner")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // TODO change defaults
    val includeSystemApps = dataStore.createValue("include.systemapps.enabled", true)


    override val mapper = PreferenceStoreMapper(
        includeSystemApps,
    )

    companion object {
        internal val TAG = logTag("AppCleaner", "Settings")
    }
}
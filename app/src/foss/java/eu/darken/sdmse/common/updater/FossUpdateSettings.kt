package eu.darken.sdmse.common.updater

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FossUpdateSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_updater_foss")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    private fun FossUpdateChecker.Update.getSetting(): DataStoreValue<Boolean> {
        return dataStore.createValue("update.${this.versionName}.dismissed", false)
    }

    suspend fun dismiss(update: FossUpdateChecker.Update) {
        update.getSetting().value(true)
    }

    suspend fun isDismissed(update: FossUpdateChecker.Update): Boolean {
        return update.getSetting().value()
    }


    companion object {
        private val TAG = logTag("Updater", "Checker", "FOSS", "Settings")
    }
}
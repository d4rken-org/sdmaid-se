package eu.darken.sdmse.common.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore by preferencesDataStore(name = "debug_settings")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val isDebugMode = dataStore.createValue("debug.enabled", BuildConfigWrap.DEBUG)
    val isTraceMode = dataStore.createValue("debug.trace.enabled", false)
    val isDryRunMode = dataStore.createValue("debug.dryrun.enabled", false)

    val recorderPath = dataStore.createValue<String?>("recorder.log.path", null)

}
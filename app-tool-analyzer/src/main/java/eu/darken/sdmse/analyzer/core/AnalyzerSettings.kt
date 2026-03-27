package eu.darken.sdmse.analyzer.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ui.LayoutMode
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AnalyzerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_analyzer")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val contentLayoutMode = dataStore.createValue("ui.content.layoutmode", LayoutMode.LINEAR, json)

    companion object {
        internal val TAG = logTag("Analyzer", "Settings")
    }
}

package eu.darken.sdmse.deduplicator.core

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
import eu.darken.sdmse.common.ui.LayoutMode
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DeduplicatorSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_deduplicator")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val allowDeleteAll = dataStore.createValue("protection.deleteall.allowed", false)
    val skipUncommon = dataStore.createValue("skip.files.uncommon", true)
    val minSizeBytes = dataStore.createValue<Long>("skip.minsize.bytes", MIN_FILE_SIZE)
    val isSleuthChecksumEnabled = dataStore.createValue("sleuth.checksum.enabled", true)
    val isSleuthPHashEnabled = dataStore.createValue("sleuth.phash.enabled", false)

    val layoutMode = dataStore.createValue("ui.list.layoutmode", LayoutMode.GRID, moshi)

    override val mapper = PreferenceStoreMapper(
        allowDeleteAll,
        minSizeBytes,
        skipUncommon,
        isSleuthChecksumEnabled,
        isSleuthPHashEnabled,
    )

    companion object {
        const val MIN_FILE_SIZE = 512 * 1024L
        internal val TAG = logTag("Deduplicator", "Settings")
    }
}
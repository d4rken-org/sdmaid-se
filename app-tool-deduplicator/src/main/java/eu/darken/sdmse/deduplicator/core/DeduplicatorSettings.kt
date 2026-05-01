@file:UseSerializers(APathSerializer::class)

package eu.darken.sdmse.deduplicator.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeduplicatorSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_deduplicator")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val allowDeleteAll = dataStore.createValue("protection.deleteall.allowed", false)
    val skipUncommon = dataStore.createValue("skip.files.uncommon", true)
    val minSizeBytes = dataStore.createValue<Long>("skip.minsize.bytes", MIN_FILE_SIZE)
    val isSleuthChecksumEnabled = dataStore.createValue("sleuth.checksum.enabled", true)
    val isSleuthPHashEnabled = dataStore.createValue("sleuth.phash.enabled", false)
    val isSleuthMediaEnabled = dataStore.createValue("sleuth.media.enabled", false)
    val scanPaths = dataStore.createValue("scan.location.paths", ScanPaths(), json)

    @Serializable
    data class ScanPaths(
        @SerialName("paths") val paths: Set<APath> = emptySet(),
    )

    val arbiterConfig = dataStore.createValue(
        key = "arbiter.config",
        defaultValue = ArbiterConfig(),
        json = json,
        fallbackToDefault = true,
    )

    @Serializable
    data class ArbiterConfig(
        @SerialName("criteria") val criteria: List<ArbiterCriterium> = listOf(
            ArbiterCriterium.DuplicateType(),
            ArbiterCriterium.PreferredPath(),
            ArbiterCriterium.MediaProvider(),
            ArbiterCriterium.Location(),
            ArbiterCriterium.Nesting(),
            ArbiterCriterium.Modified(),
            ArbiterCriterium.Size(),
        ),
    )

    val layoutMode = dataStore.createValue("ui.list.layoutmode", LayoutMode.GRID, json)

    val isDirectoryViewEnabled = dataStore.createValue("ui.cluster.directoryview.enabled", false)

    companion object {
        const val MIN_FILE_SIZE = 512 * 1024L
        internal val TAG = logTag("Deduplicator", "Settings")
    }
}

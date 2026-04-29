@file:UseSerializers(APathSerializer::class)

package eu.darken.sdmse.squeezer.core

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SqueezerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_squeezer")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val minSizeBytes = dataStore.createValue<Long>("filter.minsize.bytes", MIN_FILE_SIZE)
    val minAge = dataStore.createValue("filter.minage", MIN_AGE_DEFAULT, json)
    val compressionQuality = dataStore.createValue("compression.quality", DEFAULT_QUALITY)
    val includeJpeg = dataStore.createValue("filter.type.jpeg.enabled", true)
    val includeWebp = dataStore.createValue("filter.type.webp.enabled", true)
    val includeVideo = dataStore.createValue("filter.type.video.enabled", false)
    val skipPreviouslyCompressed = dataStore.createValue("skip.previously.compressed", true)
    val writeExifMarker = dataStore.createValue("compression.exif.marker.enabled", false)
    val scanPaths = dataStore.createValue("scan.location.paths", ScanPaths(), json)
    val layoutMode = dataStore.createValue("ui.list.layoutmode", LayoutMode.GRID, json)

    @Serializable
    data class ScanPaths(
        @SerialName("paths") val paths: Set<APath> = emptySet(),
    )

    companion object {
        const val MIN_FILE_SIZE = 512 * 1024L
        const val DEFAULT_QUALITY = 80
        val MIN_AGE_DEFAULT: Duration = Duration.ofDays(90)
        internal val TAG = logTag("Squeezer", "Settings")
    }
}

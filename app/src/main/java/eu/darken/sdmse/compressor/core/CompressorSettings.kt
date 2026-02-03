package eu.darken.sdmse.compressor.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.ui.LayoutMode
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class CompressorSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_compressor")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val minSizeBytes = dataStore.createValue<Long>("filter.minsize.bytes", MIN_FILE_SIZE)
    val minAge = dataStore.createValue("filter.minage", MIN_AGE_DEFAULT, moshi)
    val compressionQuality = dataStore.createValue("compression.quality", DEFAULT_QUALITY)
    val includeJpeg = dataStore.createValue("filter.type.jpeg.enabled", true)
    val includeWebp = dataStore.createValue("filter.type.webp.enabled", true)
    val skipPreviouslyCompressed = dataStore.createValue("skip.previously.compressed", true)
    val writeExifMarker = dataStore.createValue("compression.exif.marker.enabled", false)
    val scanPaths = dataStore.createValue("scan.location.paths", ScanPaths(), moshi)
    val layoutMode = dataStore.createValue("ui.list.layoutmode", LayoutMode.GRID, moshi)

    @JsonClass(generateAdapter = true)
    data class ScanPaths(
        @Json(name = "paths") val paths: Set<APath> = emptySet(),
    )

    override val mapper = PreferenceStoreMapper(
        minSizeBytes,
        minAge,
        compressionQuality,
        includeJpeg,
        includeWebp,
        skipPreviouslyCompressed,
        writeExifMarker,
        scanPaths,
    )

    companion object {
        const val MIN_FILE_SIZE = 512 * 1024L
        const val DEFAULT_QUALITY = 80
        val MIN_AGE_DEFAULT: Duration = Duration.ofDays(90)
        internal val TAG = logTag("Compressor", "Settings")
    }
}

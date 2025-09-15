package eu.darken.sdmse.common.clutter.manual

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.openAsset
import okio.IOException
import okio.buffer
import javax.inject.Inject

@Reusable
class JsonMarkerParser @Inject constructor(
    @ApplicationContext private val context: Context,
    baseMoshi: Moshi,
) {
    private val moshi = baseMoshi

    private val adapter by lazy {
        moshi.adapter<List<JsonMarkerGroup>>(
            Types.newParameterizedType(List::class.java, JsonMarkerGroup::class.java)
        )
    }

    fun fromAssets(assetPath: String): List<JsonMarkerGroup> {
        val startTime = System.currentTimeMillis()

        val markers: List<JsonMarkerGroup> = try {
            context.openAsset(assetPath).buffer().use { adapter.fromJson(it)!! }
        } catch (e: Exception) {
            throw IOException("Failed to load asset: $assetPath", e)
        }

        val stopTimeDeserialization = System.currentTimeMillis()
        log(TAG) { "Deserialization took ${stopTimeDeserialization - startTime}ms for ${markers.size} entries" }
        return markers
    }

    companion object {
        val TAG: String = logTag("Clutter", "RawManualMarkerParser")
    }
}
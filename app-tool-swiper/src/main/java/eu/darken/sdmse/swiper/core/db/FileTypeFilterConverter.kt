package eu.darken.sdmse.swiper.core.db

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.swiper.core.FileTypeFilter
import javax.inject.Inject

@Reusable
@ProvidedTypeConverter
class FileTypeFilterConverter @Inject constructor(
    moshi: Moshi,
) {
    private val adapter = moshi.adapter<FileTypeFilter>()

    @TypeConverter
    fun from(value: FileTypeFilter?): String? {
        if (value == null) return null
        return adapter.toJson(value)
    }

    @TypeConverter
    fun to(value: String?): FileTypeFilter? {
        if (value == null) return null
        return try {
            adapter.fromJson(value)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to parse FileTypeFilter (${value.length} chars), falling back to no filter" }
            null
        }
    }

    companion object {
        private const val TAG = "FileTypeFilterConverter"
    }
}

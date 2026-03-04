package eu.darken.sdmse.common.room

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.Reusable
import eu.darken.sdmse.common.files.APath
import javax.inject.Inject

@Reusable
@ProvidedTypeConverter
class APathListTypeConverter @Inject constructor(
    moshi: Moshi,
) {
    private val adapter = moshi.adapter<List<APath>>()

    @TypeConverter
    fun from(value: List<APath>): String = adapter.toJson(value)

    @TypeConverter
    fun to(value: String): List<APath> = adapter.fromJson(value)!!
}
package eu.darken.sdmse.common.room

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import dagger.Reusable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Reusable
@ProvidedTypeConverter
class APathListTypeConverter @Inject constructor(
    private val json: Json,
) {
    private val serializer = ListSerializer(APathSerializer)

    @TypeConverter
    fun from(value: List<APath>): String = json.encodeToString(serializer, value)

    @TypeConverter
    fun to(value: String): List<APath> = json.decodeFromString(serializer, value)
}

package eu.darken.sdmse.common.room

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import dagger.Reusable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Reusable
@ProvidedTypeConverter
class APathTypeConverter @Inject constructor(
    private val json: Json,
) {
    @TypeConverter
    fun from(value: APath): String = json.encodeToString(APathSerializer, value)

    @TypeConverter
    fun to(value: String): APath = json.decodeFromString(APathSerializer, value)
}

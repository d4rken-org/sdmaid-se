package eu.darken.sdmse.squeezer.core.history

import androidx.room.TypeConverter

class CompressionHistoryConverters {

    @TypeConverter
    fun fromOutcome(outcome: CompressionHistoryEntity.Outcome): String = outcome.name

    @TypeConverter
    fun toOutcome(value: String): CompressionHistoryEntity.Outcome =
        CompressionHistoryEntity.Outcome.valueOf(value)
}

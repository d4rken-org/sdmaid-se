package eu.darken.sdmse.common.room

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverter {
    @TypeConverter
    fun fromValue(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toValue(instant: Instant?): Long? = instant?.toEpochMilli()
}
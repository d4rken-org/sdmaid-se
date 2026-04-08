package eu.darken.sdmse.swiper.core.db

import androidx.room.TypeConverter
import eu.darken.sdmse.swiper.core.SortOrder

class SortOrderConverter {
    @TypeConverter
    fun fromValue(value: String): SortOrder = SortOrder.valueOf(value)

    @TypeConverter
    fun toValue(value: SortOrder): String = value.name
}

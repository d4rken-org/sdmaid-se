package eu.darken.sdmse.stats.core.db.converter

import androidx.room.TypeConverter
import eu.darken.sdmse.stats.core.ReportId
import java.util.UUID

class ReportIdTypeConverter {
    @TypeConverter
    fun from(value: ReportId): String = value.toString()

    @TypeConverter
    fun to(value: String): ReportId = UUID.fromString(value)
}
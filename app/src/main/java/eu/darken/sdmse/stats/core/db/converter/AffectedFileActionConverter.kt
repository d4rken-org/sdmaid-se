package eu.darken.sdmse.stats.core.db.converter

import androidx.room.TypeConverter
import eu.darken.sdmse.stats.core.AffectedPath

class AffectedFileActionConverter {
    @TypeConverter
    fun from(value: AffectedPath.Action): String = value.toString()

    @TypeConverter
    fun to(value: String): AffectedPath.Action = AffectedPath.Action.valueOf(value)
}
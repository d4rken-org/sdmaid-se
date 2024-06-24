package eu.darken.sdmse.stats.core.db

import androidx.room.TypeConverter
import eu.darken.sdmse.main.core.SDMTool

class SDMToolTypeConverter {
    @TypeConverter
    fun from(value: SDMTool.Type): String = value.toString()

    @TypeConverter
    fun to(value: String): SDMTool.Type = SDMTool.Type.valueOf(value)
}
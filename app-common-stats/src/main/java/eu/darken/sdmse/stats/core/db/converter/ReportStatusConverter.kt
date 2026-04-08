package eu.darken.sdmse.stats.core.db.converter

import androidx.room.TypeConverter
import eu.darken.sdmse.stats.core.Report

class ReportStatusConverter {
    @TypeConverter
    fun from(value: Report.Status): String = value.name

    @TypeConverter
    fun to(value: String): Report.Status = Report.Status.valueOf(value)
}
package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class OffsetDateTimeAdapter {
    @ToJson
    fun toJson(value: OffsetDateTime): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value)

    @FromJson
    fun fromJson(value: String): OffsetDateTime = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
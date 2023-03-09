package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Duration

class DurationAdapter {
    @ToJson
    fun toJson(value: Duration): String = value.toString()

    @FromJson
    fun fromJson(raw: String): Duration = Duration.parse(raw)
}
package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant

class InstantAdapter {
    @ToJson
    fun toJson(value: Instant): String = value.toString()

    @FromJson
    fun fromJson(raw: String): Instant = Instant.parse(raw)
}
package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.*

class UUIDAdapter {
    @ToJson
    fun toJson(value: UUID): String = value.toString()

    @FromJson
    fun fromJson(raw: String): UUID = UUID.fromString(raw)
}
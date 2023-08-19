package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

class RegexAdapter {

    @ToJson
    fun toJson(value: Regex): Wrapper = Wrapper(
        pattern = value.pattern,
        options = value.options,
    )

    @FromJson
    fun fromJson(raw: Wrapper): Regex = Regex(
        pattern = raw.pattern,
        options = raw.options
    )

    @JsonClass(generateAdapter = true)
    data class Wrapper(
        @Json(name = "pattern") val pattern: String,
        @Json(name = "options") val options: Set<RegexOption>
    )
}
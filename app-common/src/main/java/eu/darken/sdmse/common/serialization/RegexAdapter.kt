package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class RegexAdapter(private val caseInsensitive: Boolean) {
    @ToJson
    fun toJson(value: Regex): String = value.toString()

    @FromJson
    fun fromJson(raw: String): Regex = if (caseInsensitive) {
        Regex(raw, RegexOption.IGNORE_CASE)
    } else {
        Regex(raw)
    }
}
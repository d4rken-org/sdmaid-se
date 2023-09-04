package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.util.Locale

class LocaleAdapter {
    @ToJson
    fun toJson(value: Locale): String = value.toLanguageTag()

    @FromJson
    fun fromJson(raw: String) = Locale.forLanguageTag(raw)
}
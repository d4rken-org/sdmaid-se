package eu.darken.sdmse.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

class RegexAdapter {

    @ToJson
    fun toJson(value: Regex): Wrapper = Wrapper(
        pattern = value.pattern,
        options = value.options.map { it.toWrapperOption() }.toSet(),
    )

    @FromJson
    fun fromJson(raw: Wrapper): Regex = Regex(
        pattern = raw.pattern,
        options = raw.options.map { it.toRegexOption() }.toSet()
    )

    private fun Wrapper.Option.toRegexOption() = when (this) {
        Wrapper.Option.IGNORE_CASE -> RegexOption.IGNORE_CASE
        Wrapper.Option.MULTILINE -> RegexOption.MULTILINE
        Wrapper.Option.LITERAL -> RegexOption.LITERAL
        Wrapper.Option.UNIX_LINES -> RegexOption.UNIX_LINES
        Wrapper.Option.COMMENTS -> RegexOption.COMMENTS
        Wrapper.Option.DOT_MATCHES_ALL -> RegexOption.DOT_MATCHES_ALL
        Wrapper.Option.CANON_EQ -> RegexOption.CANON_EQ
    }

    private fun RegexOption.toWrapperOption() = when (this) {
        RegexOption.IGNORE_CASE -> Wrapper.Option.IGNORE_CASE
        RegexOption.MULTILINE -> Wrapper.Option.MULTILINE
        RegexOption.LITERAL -> Wrapper.Option.LITERAL
        RegexOption.UNIX_LINES -> Wrapper.Option.UNIX_LINES
        RegexOption.COMMENTS -> Wrapper.Option.COMMENTS
        RegexOption.DOT_MATCHES_ALL -> Wrapper.Option.DOT_MATCHES_ALL
        RegexOption.CANON_EQ -> Wrapper.Option.CANON_EQ
    }

    @JsonClass(generateAdapter = true)
    data class Wrapper(
        @Json(name = "pattern") val pattern: String,
        @Json(name = "options") val options: Set<Option>
    ) {
        @JsonClass(generateAdapter = false)
        enum class Option {
            @Json(name = "IGNORE_CASE") IGNORE_CASE,
            @Json(name = "MULTILINE") MULTILINE,
            @Json(name = "LITERAL") LITERAL,
            @Json(name = "UNIX_LINES") UNIX_LINES,
            @Json(name = "COMMENTS") COMMENTS,
            @Json(name = "DOT_MATCHES_ALL") DOT_MATCHES_ALL,
            @Json(name = "CANON_EQ") CANON_EQ,
            ;
        }
    }
}
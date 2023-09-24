package eu.darken.sdmse.appcontrol.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FilterSettings(
    val tags: Set<Tag> = setOf(
        Tag.USER,
        Tag.ENABLED,
    ),
) {
    @JsonClass(generateAdapter = false)
    enum class Tag {
        @Json(name = "USER") USER,
        @Json(name = "SYSTEM") SYSTEM,
        @Json(name = "ENABLED") ENABLED,
        @Json(name = "DISABLED") DISABLED,
        @Json(name = "ACTIVE") ACTIVE,
        ;
    }
}

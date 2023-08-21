package eu.darken.sdmse.appcontrol.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SortSettings(
    @Json(name = "mode") val mode: Mode = Mode.LAST_UPDATE,
    @Json(name = "reversed") val reversed: Boolean = true,
) {
    @JsonClass(generateAdapter = false)
    enum class Mode {
        @Json(name = "NAME") NAME,
        @Json(name = "LAST_UPDATE") LAST_UPDATE,
        @Json(name = "INSTALLED_AT") INSTALLED_AT,
        @Json(name = "PACKAGENAME") PACKAGENAME,
        ;
    }
}
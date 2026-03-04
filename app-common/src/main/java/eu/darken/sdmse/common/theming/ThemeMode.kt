package eu.darken.sdmse.common.theming

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class ThemeMode {
    @Json(name = "SYSTEM") SYSTEM,
    @Json(name = "DARK") DARK,
    @Json(name = "LIGHT") LIGHT,
}

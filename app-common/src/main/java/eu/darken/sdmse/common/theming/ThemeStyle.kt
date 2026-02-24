package eu.darken.sdmse.common.theming

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class ThemeStyle {
    @Json(name = "DEFAULT") DEFAULT,
    @Json(name = "MATERIAL_YOU") MATERIAL_YOU,
}

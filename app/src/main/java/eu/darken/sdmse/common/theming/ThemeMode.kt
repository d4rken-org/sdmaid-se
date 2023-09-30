package eu.darken.sdmse.common.theming

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.preferences.EnumPreference

@JsonClass(generateAdapter = false)
enum class ThemeMode(override val label: CaString) : EnumPreference<ThemeMode> {
    @Json(name = "SYSTEM") SYSTEM(R.string.ui_theme_mode_system_label.toCaString()),
    @Json(name = "DARK") DARK(R.string.ui_theme_mode_dark_label.toCaString()),
    @Json(name = "LIGHT") LIGHT(R.string.ui_theme_mode_light_label.toCaString()),
    ;
}


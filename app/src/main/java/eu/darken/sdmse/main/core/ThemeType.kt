package eu.darken.sdmse.main.core

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.R

@JsonClass(generateAdapter = false)
enum class ThemeType(val identifier: String) {
    @Json(name = "SYSTEM") SYSTEM("SYSTEM"),
    @Json(name = "DARK") DARK("DARK"),
    @Json(name = "LIGHT") LIGHT("LIGHT"),
    ;
}

@get:StringRes
val ThemeType.labelRes: Int
    get() = when (this) {
        ThemeType.SYSTEM -> R.string.ui_theme_system_label
        ThemeType.DARK -> R.string.ui_theme_dark_label
        ThemeType.LIGHT -> R.string.ui_theme_light_label
    }
package eu.darken.sdmse.common.theming

import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeColor(override val label: CaString) : EnumPreference<ThemeColor> {
    @SerialName("GREEN") GREEN(R.string.ui_theme_color_green_label.toCaString()),
    @SerialName("SCHOLAR") SCHOLAR(R.string.ui_theme_color_scholar_label.toCaString()),
}

package eu.darken.sdmse.common.theming

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeStyle {
    @SerialName("DEFAULT") DEFAULT,
    @SerialName("MATERIAL_YOU") MATERIAL_YOU,
    @SerialName("MEDIUM_CONTRAST") MEDIUM_CONTRAST,
    @SerialName("HIGH_CONTRAST") HIGH_CONTRAST,
}

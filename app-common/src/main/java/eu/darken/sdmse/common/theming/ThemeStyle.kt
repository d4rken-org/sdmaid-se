package eu.darken.sdmse.common.theming

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeStyle {
    @SerialName("DEFAULT") DEFAULT,
    @SerialName("MATERIAL_YOU") MATERIAL_YOU,
}

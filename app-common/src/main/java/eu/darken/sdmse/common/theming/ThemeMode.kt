package eu.darken.sdmse.common.theming

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    @SerialName("SYSTEM") SYSTEM,
    @SerialName("DARK") DARK,
    @SerialName("LIGHT") LIGHT,
}

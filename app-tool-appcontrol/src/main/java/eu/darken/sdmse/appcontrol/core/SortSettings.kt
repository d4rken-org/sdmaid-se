package eu.darken.sdmse.appcontrol.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SortSettings(
    @SerialName("mode") val mode: Mode = Mode.LAST_UPDATE,
    @SerialName("reversed") val reversed: Boolean = true,
) {
    @Serializable
    enum class Mode {
        @SerialName("NAME") NAME,
        @SerialName("LAST_UPDATE") LAST_UPDATE,
        @SerialName("INSTALLED_AT") INSTALLED_AT,
        @SerialName("PACKAGENAME") PACKAGENAME,
        @SerialName("SIZE") SIZE,
        @SerialName("SCREEN_TIME") SCREEN_TIME,
        ;
    }
}
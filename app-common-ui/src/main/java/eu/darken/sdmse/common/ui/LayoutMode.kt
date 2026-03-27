package eu.darken.sdmse.common.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LayoutMode {
    @SerialName("LINEAR") LINEAR,
    @SerialName("GRID") GRID,
    ;
}

package eu.darken.sdmse.common.clutter.manual

import androidx.annotation.Keep
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.Marker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class JsonMarkerGroup(
    @SerialName("pkgs") val pkgs: List<String>? = null,
    @SerialName("regexPkgs") val regexPkgs: List<String>? = null,
    @SerialName("mrks") val mrks: List<JsonMarker>,
) {

    init {
        require(!(pkgs.isNullOrEmpty() && regexPkgs.isNullOrEmpty())) { "No pkg matching defined: $this" }
        require(mrks.isNotEmpty()) { "Group contains no markers: $this" }
    }

    @Serializable
    @Keep
    data class JsonMarker(
        @SerialName("loc") val areaType: DataArea.Type,
        @SerialName("path") val path: String? = null,
        @SerialName("contains") val contains: String? = null,
        @SerialName("regex") val regex: String? = null,
        @SerialName("flags") val flags: Set<Marker.Flag>? = null,
    ) {
        init {
            require(path != null || contains != null || regex != null) { "No path matching defined: $this" }
        }
    }
}
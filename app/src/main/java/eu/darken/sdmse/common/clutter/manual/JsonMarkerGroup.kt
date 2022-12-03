package eu.darken.sdmse.common.clutter.manual

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.Marker

@Keep
@JsonClass(generateAdapter = true)
data class JsonMarkerGroup constructor(
    @Json(name = "pkgs") val pkgs: List<String>?,
    @Json(name = "regexPkgs") val regexPkgs: List<String>?,
    @Json(name = "mrks") val mrks: List<JsonMarker>,
) {

    init {
        require(!(pkgs.isNullOrEmpty() && regexPkgs.isNullOrEmpty())) { "No pkg matching defined: $this" }
        require(mrks.isNotEmpty()) { "Group contains no markers: $this" }
    }

    @Keep
    @JsonClass(generateAdapter = true)
    data class JsonMarker(
        @Json(name = "loc") val areaType: DataArea.Type,
        @Json(name = "path") val path: String?,
        @Json(name = "contains") val contains: String?,
        @Json(name = "regex") val regex: String?,
        @Json(name = "flags") val flags: Set<Marker.Flag>?,
    ) {
        init {
            require(path != null || contains != null || regex != null) { "No path matching defined: $this" }
        }
    }
}
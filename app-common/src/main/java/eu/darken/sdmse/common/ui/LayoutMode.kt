package eu.darken.sdmse.common.ui

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class LayoutMode {
    LINEAR,
    GRID,
    ;
}
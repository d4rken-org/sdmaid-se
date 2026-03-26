package eu.darken.sdmse.common.previews

import eu.darken.sdmse.common.previews.item.PreviewItem
import kotlinx.serialization.Serializable

@Serializable
data class PreviewRoute(
    val options: PreviewOptions,
)

@Serializable
data class PreviewItemRoute(
    val item: PreviewItem,
)

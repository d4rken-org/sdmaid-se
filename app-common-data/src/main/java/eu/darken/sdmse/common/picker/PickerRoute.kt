package eu.darken.sdmse.common.picker

import kotlinx.serialization.Serializable

@Serializable
data class PickerRoute(
    val request: PickerRequest,
)

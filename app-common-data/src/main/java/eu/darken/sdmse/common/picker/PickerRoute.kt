package eu.darken.sdmse.common.picker

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class PickerRoute(
    val request: PickerRequest,
) : NavigationDestination

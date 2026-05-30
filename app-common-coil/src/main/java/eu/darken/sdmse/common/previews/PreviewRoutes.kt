package eu.darken.sdmse.common.previews

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class PreviewRoute(
    val options: PreviewOptions,
) : NavigationDestination

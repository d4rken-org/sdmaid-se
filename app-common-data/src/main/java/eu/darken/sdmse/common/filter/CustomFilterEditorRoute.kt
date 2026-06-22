package eu.darken.sdmse.common.filter

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class CustomFilterEditorRoute(
    val initial: CustomFilterEditorOptions? = null,
    val identifier: String? = null,
) : NavigationDestination

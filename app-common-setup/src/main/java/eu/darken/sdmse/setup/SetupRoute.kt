package eu.darken.sdmse.setup

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data class SetupRoute(
    val options: SetupScreenOptions? = null,
) : NavigationDestination

package eu.darken.sdmse.squeezer.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SqueezerSetupRoute : NavigationDestination

@Serializable
data object SqueezerListRoute : NavigationDestination

@Serializable
data object SqueezerSettingsRoute : NavigationDestination

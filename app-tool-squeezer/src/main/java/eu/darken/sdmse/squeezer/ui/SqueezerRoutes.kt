package eu.darken.sdmse.squeezer.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
data object SqueezerSetupRoute

@Serializable
data object SqueezerListRoute

@Serializable
data object SqueezerSettingsRoute : NavigationDestination

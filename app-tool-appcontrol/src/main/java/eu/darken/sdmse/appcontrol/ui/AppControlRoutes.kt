package eu.darken.sdmse.appcontrol.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable

@Serializable
data object AppControlSettingsRoute : NavigationDestination

@Serializable
data class AppActionRoute(
    val installId: InstallId,
) : NavigationDestination

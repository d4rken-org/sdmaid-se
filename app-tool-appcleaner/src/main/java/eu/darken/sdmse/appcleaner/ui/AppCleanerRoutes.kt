package eu.darken.sdmse.appcleaner.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable

@Serializable
data object AppCleanerSettingsRoute : NavigationDestination

@Serializable
data object AppCleanerListRoute : NavigationDestination

@Serializable
data class AppJunkDetailsRoute(
    val identifier: InstallId? = null,
) : NavigationDestination

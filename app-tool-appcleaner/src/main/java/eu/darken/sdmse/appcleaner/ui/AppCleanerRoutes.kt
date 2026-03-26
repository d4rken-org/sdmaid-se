package eu.darken.sdmse.appcleaner.ui

import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable

@Serializable
data object AppCleanerListRoute

@Serializable
data class AppJunkDetailsRoute(
    val identifier: InstallId? = null,
)

@Serializable
data class AppJunkRoute(
    val identifier: InstallId,
)

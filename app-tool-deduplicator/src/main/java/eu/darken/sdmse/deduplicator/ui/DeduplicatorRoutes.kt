package eu.darken.sdmse.deduplicator.ui

import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.serialization.Serializable

@Serializable
data object DeduplicatorSettingsRoute : NavigationDestination

@Serializable
data object DeduplicatorListRoute : NavigationDestination

@Serializable
data class DeduplicatorDetailsRoute(
    val identifier: Duplicate.Cluster.Id? = null,
) : NavigationDestination

@Serializable
data object ArbiterConfigRoute : NavigationDestination

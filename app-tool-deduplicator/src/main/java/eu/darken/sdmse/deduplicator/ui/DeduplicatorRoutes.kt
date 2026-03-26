package eu.darken.sdmse.deduplicator.ui

import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.serialization.Serializable

@Serializable
data object DeduplicatorListRoute

@Serializable
data class DeduplicatorDetailsRoute(
    val identifier: Duplicate.Cluster.Id? = null,
)

@Serializable
data class ClusterRoute(
    val identifier: Duplicate.Cluster.Id,
)

@Serializable
data object ArbiterConfigRoute

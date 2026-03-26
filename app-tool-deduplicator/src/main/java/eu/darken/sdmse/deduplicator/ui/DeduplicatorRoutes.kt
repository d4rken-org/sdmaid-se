package eu.darken.sdmse.deduplicator.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data object DeduplicatorListRoute

@Serializable
data class DeduplicatorDetailsRoute(
    val identifier: Duplicate.Cluster.Id? = null,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<Duplicate.Cluster.Id?>() to serializableNavType(Duplicate.Cluster.Id.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<DeduplicatorDetailsRoute>(typeMap)
    }
}

@Serializable
data class ClusterRoute(
    val identifier: Duplicate.Cluster.Id,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<Duplicate.Cluster.Id>() to serializableNavType(Duplicate.Cluster.Id.serializer()),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<ClusterRoute>(typeMap)
    }
}

@Serializable
data object ArbiterConfigRoute

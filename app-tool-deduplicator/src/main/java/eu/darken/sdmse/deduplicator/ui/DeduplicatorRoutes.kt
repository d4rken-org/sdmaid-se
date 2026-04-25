package eu.darken.sdmse.deduplicator.ui

import androidx.navigation.NavType
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.deduplicator.core.Duplicate
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data object DeduplicatorSettingsRoute : NavigationDestination

@Serializable
data object DeduplicatorListRoute : NavigationDestination

@Serializable
data class DeduplicatorDetailsRoute(
    val identifier: Duplicate.Cluster.Id? = null,
) : NavigationDestination {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<Duplicate.Cluster.Id?>() to serializableNavType(Duplicate.Cluster.Id.serializer(), isNullableAllowed = true),
        )
    }
}

@Serializable
data object ArbiterConfigRoute : NavigationDestination

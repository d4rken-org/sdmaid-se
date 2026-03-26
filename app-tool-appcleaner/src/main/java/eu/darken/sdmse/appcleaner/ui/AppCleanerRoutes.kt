package eu.darken.sdmse.appcleaner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data object AppCleanerListRoute

@Serializable
data class AppJunkDetailsRoute(
    val identifier: InstallId? = null,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<InstallId?>() to serializableNavType(InstallId.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<AppJunkDetailsRoute>(typeMap)
    }
}

@Serializable
data class AppJunkRoute(
    val identifier: InstallId,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<InstallId>() to serializableNavType(InstallId.serializer()),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<AppJunkRoute>(typeMap)
    }
}

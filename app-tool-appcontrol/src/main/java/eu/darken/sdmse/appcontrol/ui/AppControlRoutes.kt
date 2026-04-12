package eu.darken.sdmse.appcontrol.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data object AppControlSettingsRoute : NavigationDestination

@Serializable
data class AppActionRoute(
    val installId: InstallId,
) : NavigationDestination {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<InstallId>() to serializableNavType(InstallId.serializer()),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<AppActionRoute>(typeMap)
    }
}

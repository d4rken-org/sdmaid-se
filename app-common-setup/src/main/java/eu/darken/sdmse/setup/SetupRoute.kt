package eu.darken.sdmse.setup

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class SetupRoute(
    val options: SetupScreenOptions? = null,
) : NavigationDestination {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<SetupScreenOptions?>() to serializableNavType(SetupScreenOptions.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<SetupRoute>(typeMap)
    }
}

package eu.darken.sdmse.common.filter

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.serializableNavType
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class CustomFilterEditorRoute(
    val initial: CustomFilterEditorOptions? = null,
    val identifier: String? = null,
) : NavigationDestination {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<CustomFilterEditorOptions?>() to serializableNavType(CustomFilterEditorOptions.serializer(), isNullableAllowed = true),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<CustomFilterEditorRoute>(typeMap)
    }
}

package eu.darken.sdmse.common.previews

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.serializableNavType
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class PreviewRoute(
    val options: PreviewOptions,
) : NavigationDestination {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<PreviewOptions>() to serializableNavType(PreviewOptions.serializer()),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<PreviewRoute>(typeMap)
    }
}

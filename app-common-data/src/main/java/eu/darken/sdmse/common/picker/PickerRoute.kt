package eu.darken.sdmse.common.picker

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.toRoute
import eu.darken.sdmse.common.navigation.serializableNavType
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class PickerRoute(
    val request: PickerRequest,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<PickerRequest>() to serializableNavType(PickerRequest.serializer()),
        )

        fun from(handle: SavedStateHandle) = handle.toRoute<PickerRoute>(typeMap)
    }
}

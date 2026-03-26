package eu.darken.sdmse.appcontrol.ui

import androidx.navigation.NavType
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class AppActionRoute(
    val installId: InstallId,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<InstallId>() to serializableNavType(InstallId.serializer()),
        )
    }
}

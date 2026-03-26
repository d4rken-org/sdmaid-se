package eu.darken.sdmse.appcleaner.ui

import androidx.navigation.NavType
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
    }
}

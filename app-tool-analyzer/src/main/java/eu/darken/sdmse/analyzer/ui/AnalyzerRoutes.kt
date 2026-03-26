package eu.darken.sdmse.analyzer.ui

import androidx.navigation.NavType
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.navigation.serializableNavType
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.storage.StorageId
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Serializable
data class StorageContentRoute(
    val storageId: StorageId,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<StorageId>() to serializableNavType(StorageId.serializer()),
        )
    }
}

@Serializable
data class AppsRoute(
    val storageId: StorageId,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<StorageId>() to serializableNavType(StorageId.serializer()),
        )
    }
}

@Serializable
data class AppDetailsRoute(
    val storageId: StorageId,
    val installId: InstallId,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<StorageId>() to serializableNavType(StorageId.serializer()),
            typeOf<InstallId>() to serializableNavType(InstallId.serializer()),
        )
    }
}

@Serializable
data class ContentRoute(
    val storageId: StorageId,
    val groupId: ContentGroup.Id,
    val installId: InstallId? = null,
) {
    companion object {
        val typeMap: Map<KType, NavType<*>> = mapOf(
            typeOf<StorageId>() to serializableNavType(StorageId.serializer()),
            typeOf<ContentGroup.Id>() to serializableNavType(ContentGroup.Id.serializer()),
            typeOf<InstallId?>() to serializableNavType(InstallId.serializer(), isNullableAllowed = true),
        )
    }
}

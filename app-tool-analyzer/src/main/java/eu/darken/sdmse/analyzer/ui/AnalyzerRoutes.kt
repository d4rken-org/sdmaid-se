package eu.darken.sdmse.analyzer.ui

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.storage.StorageId
import kotlinx.serialization.Serializable

@Serializable
data class StorageContentRoute(
    val storageId: StorageId,
)

@Serializable
data class AppsRoute(
    val storageId: StorageId,
)

@Serializable
data class AppDetailsRoute(
    val storageId: StorageId,
    val installId: InstallId,
)

@Serializable
data class ContentRoute(
    val storageId: StorageId,
    val groupId: ContentGroup.Id,
    val installId: InstallId? = null,
)

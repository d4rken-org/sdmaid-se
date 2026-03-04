package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.storage.StorageId

sealed interface ContentCategory {
    val storageId: StorageId
    val spaceUsed: Long
    val groups: Collection<ContentGroup>
}
package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.storage.StorageId

data class MediaCategory(
    override val storageId: StorageId,
    override val groups: Collection<ContentGroup>,
    val spaceUsedOverride: Long? = null,
    val isReadOnly: Boolean = false,
) : ContentCategory {

    override val spaceUsed: Long
        get() = spaceUsedOverride ?: groups.sumOf { it.groupSize }
}

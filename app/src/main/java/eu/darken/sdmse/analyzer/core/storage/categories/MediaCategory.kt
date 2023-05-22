package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage

data class MediaCategory(
    override val storageId: DeviceStorage.Id,
    val spaceUsedOverride: Long? = null,
    override val groups: Collection<ContentGroup>,
) : ContentCategory {

    override val spaceUsed: Long
        get() = spaceUsedOverride ?: groups.sumOf { it.groupSize }
}
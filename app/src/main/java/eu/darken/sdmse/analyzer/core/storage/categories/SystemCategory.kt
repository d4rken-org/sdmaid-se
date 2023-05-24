package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage

data class SystemCategory(
    override val storageId: DeviceStorage.Id,
    override val spaceUsed: Long,
) : ContentCategory {
    override val groups: Collection<ContentGroup>
        get() = emptyList()
}
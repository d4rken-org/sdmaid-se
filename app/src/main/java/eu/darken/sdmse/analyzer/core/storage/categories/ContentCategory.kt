package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage

sealed interface ContentCategory {
    val storageId: DeviceStorage.Id
    val spaceUsed: Long
    val groups: Collection<ContentGroup>
}
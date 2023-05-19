package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.device.DeviceStorage

data class MediaCategory(
    override val storageId: DeviceStorage.Id,
    override val spaceUsed: Long,
) : ContentCategory
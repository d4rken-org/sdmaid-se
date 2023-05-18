package eu.darken.sdmse.analyzer.core.storage.types

import eu.darken.sdmse.analyzer.core.device.DeviceStorage

data class SystemContent(
    override val storageId: DeviceStorage.Id,
    override val spaceUsed: Long,
) : StorageContent
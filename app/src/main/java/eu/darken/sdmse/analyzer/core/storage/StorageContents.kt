package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.types.StorageContent

data class StorageContents(
    val deviceStorage: DeviceStorage,
    val contents: Set<StorageContent>
)
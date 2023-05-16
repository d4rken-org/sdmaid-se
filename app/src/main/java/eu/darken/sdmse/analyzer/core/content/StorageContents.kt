package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.analyzer.core.content.types.StorageContent
import eu.darken.sdmse.analyzer.core.device.DeviceStorage

data class StorageContents(
    val deviceStorage: DeviceStorage,
    val contents: Set<StorageContent>
)
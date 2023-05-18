package eu.darken.sdmse.analyzer.core.storage.types

import eu.darken.sdmse.analyzer.core.device.DeviceStorage

sealed interface StorageContent {
    val storageId: DeviceStorage.Id
    val spaceUsed: Long
}
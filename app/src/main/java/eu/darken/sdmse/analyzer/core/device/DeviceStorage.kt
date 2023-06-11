package eu.darken.sdmse.analyzer.core.device

import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.storage.StorageId

data class DeviceStorage(
    val id: StorageId,
    val label: CaString,
    val type: Type,
    val hardware: Hardware,
    val spaceCapacity: Long,
    val spaceFree: Long,
    val setupIncomplete: Boolean
) {
    val spaceUsed: Long
        get() = spaceCapacity - spaceFree

    enum class Type {
        PRIMARY,
        SECONDARY,
        ;
    }

    enum class Hardware {
        BUILT_IN,
        SDCARD,
        ;
    }

}



package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.common.ca.CaString

data class DeviceStorage(
    val id: String,
    val label: CaString,
    val description: CaString,
    val hardwareType: HardwareType,
    val spaceCapacity: Long,
    val spaceFree: Long,
) {
    val spaceUsed: Long
        get() = spaceCapacity - spaceFree

    enum class HardwareType {
        BUILT_IN,
        SDCARD,
        ;
    }
}

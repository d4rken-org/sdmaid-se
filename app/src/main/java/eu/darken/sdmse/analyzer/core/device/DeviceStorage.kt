package eu.darken.sdmse.analyzer.core.device

import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import kotlinx.parcelize.Parcelize
import java.util.UUID

data class DeviceStorage(
    val id: Id,
    val label: CaString,
    val type: Type,
    val hardware: Hardware,
    val spaceCapacity: Long,
    val spaceFree: Long,
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

    @Parcelize
    data class Id(
        val value: String
    ) : Parcelable {
        val asUUID: UUID
            get() = UUID.fromString(value)
    }
}



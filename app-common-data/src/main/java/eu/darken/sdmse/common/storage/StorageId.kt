package eu.darken.sdmse.common.storage

import android.os.Parcelable
import eu.darken.sdmse.common.serialization.UUIDSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Parcelize
data class StorageId(
    val internalId: String?,
    @Serializable(with = UUIDSerializer::class) val externalId: UUID,
) : Parcelable {

    companion object {
        // Mirrors android.os.storage.StorageManager.FAT_UUID_PREFIX — synthesised for FAT/exFAT volumes
        // whose fsUuid is a 4+4-hex label (e.g. "EFFD-F4D5") rather than a real 128-bit UUID.
        const val FAT_UUID_PREFIX = "fafafafa-fafa-5afa-8afa-fafa"

        fun parseVolumeUuid(fsUuid: String?): UUID? {
            if (fsUuid == null) return null
            return try {
                UUID.fromString(fsUuid)
            } catch (_: IllegalArgumentException) {
                try {
                    UUID.fromString("$FAT_UUID_PREFIX${fsUuid.replace("-", "")}")
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
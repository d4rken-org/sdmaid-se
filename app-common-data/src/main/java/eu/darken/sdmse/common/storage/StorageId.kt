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
        fun parseVolumeUuid(fsUuid: String?): UUID? {
            if (fsUuid == null) return null
            return try {
                UUID.fromString(fsUuid)
            } catch (_: IllegalArgumentException) {
                try {
                    // StorageManager.FAT_UUID_PREFIX style fallback
                    UUID.fromString("fafafafa-fafa-5afa-8afa-fafa${fsUuid.replace("-", "")}")
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
package eu.darken.sdmse.common.storage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class StorageId(
    val internalId: String?,
    val externalId: UUID,
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
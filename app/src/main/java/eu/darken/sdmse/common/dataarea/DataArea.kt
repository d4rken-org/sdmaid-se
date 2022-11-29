package eu.darken.sdmse.common.dataarea

import android.os.Parcelable
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.parcelize.Parcelize

@Parcelize
data class DataArea(
    val path: APath,
    val type: DataAreaType,
    val label: String = type.name,
    val flags: Set<Flag> = emptySet(),
    /**
     * -1 location has no user seperation
     * 0 admin user/owner
     * X other users
     */
    val userHandle: UserHandle2,
    val restrictedChatset: Boolean = type.isPublic
) : Parcelable {

    enum class Flag {
        PRIMARY, SECONDARY, EMULATED
    }
}

package eu.darken.sdmse.common.user

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserProfile2(
    val handle: UserHandle2,
    val label: String? = null,
    val code: String? = null,
    val isRunning: Boolean = true,
) : Parcelable
package eu.darken.sdmse.common.user

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserHandle2(
    val userId: Int = 0
) : Parcelable
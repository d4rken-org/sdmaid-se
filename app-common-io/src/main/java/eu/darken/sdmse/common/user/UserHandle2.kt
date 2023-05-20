package eu.darken.sdmse.common.user

import android.os.Parcel
import android.os.Parcelable
import android.os.UserHandle
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserHandle2(
    val handleId: Int = 0
) : Parcelable {

    fun asUserHandle(): UserHandle {
        val userParcel = Parcel.obtain().apply {
            writeInt(handleId)
            setDataPosition(0)
        }
        return UserHandle(userParcel)
    }

}
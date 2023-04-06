package eu.darken.sdmse.common.files

import android.os.Parcel
import android.os.Parcelable
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Ownership(
    val userId: Long,
    val groupId: Long,
    val userName: String? = null,
    val groupName: String? = null
) : Parcelable {

    constructor(userId: Int, groupId: Int, userName: String? = null, groupName: String? = null)
            : this(userId.toLong(), groupId.toLong(), userName, groupName)

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString(),
        parcel.readString()
    )

    init {
        require(userId >= 0) { "User ID ($userId) needs to be >= 0" }
        require(groupId >= 0) { "Group ID ($groupId) needs to be >= 0" }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(userId)
        parcel.writeLong(groupId)
        parcel.writeString(userName)
        parcel.writeString(groupName)
    }

    override fun describeContents(): Int = 0

    override fun toString(): String = "Ownership($userName/$userId|$groupName/$groupId)"

    companion object CREATOR : Parcelable.Creator<Ownership> {
        override fun createFromParcel(parcel: Parcel): Ownership {
            return Ownership(parcel)
        }

        override fun newArray(size: Int): Array<Ownership?> {
            return arrayOfNulls(size)
        }
    }
}
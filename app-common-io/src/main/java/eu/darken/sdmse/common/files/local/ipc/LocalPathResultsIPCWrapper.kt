package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable

data class LocalPathResultsIPCWrapper(
    val payload: List<LocalPathResult>,
) : Parcelable, IPCWrapper {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathResult::class.java.classLoader)!!
            .toList() as List<LocalPathResult>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathResultsIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): LocalPathResultsIPCWrapper =
            LocalPathResultsIPCWrapper(parcel)

        override fun newArray(size: Int): Array<LocalPathResultsIPCWrapper?> = arrayOfNulls(size)
    }
}

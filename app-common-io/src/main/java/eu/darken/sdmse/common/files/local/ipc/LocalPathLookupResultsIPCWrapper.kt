package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable

data class LocalPathLookupResultsIPCWrapper(
    val payload: List<LocalPathLookupResult>,
) : Parcelable, IPCWrapper {
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookupResult::class.java.classLoader)!!
            .toList() as List<LocalPathLookupResult>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathLookupResultsIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupResultsIPCWrapper =
            LocalPathLookupResultsIPCWrapper(parcel)

        override fun newArray(size: Int): Array<LocalPathLookupResultsIPCWrapper?> = arrayOfNulls(size)
    }
}
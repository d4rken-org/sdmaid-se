package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable

data class LocalPathLookupExtendedResultsIPCWrapper(
    val payload: List<LocalPathLookupExtendedResult>,
) : Parcelable, IPCWrapper {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookupExtendedResult::class.java.classLoader)!!
            .toList() as List<LocalPathLookupExtendedResult>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathLookupExtendedResultsIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupExtendedResultsIPCWrapper =
            LocalPathLookupExtendedResultsIPCWrapper(parcel)

        override fun newArray(size: Int): Array<LocalPathLookupExtendedResultsIPCWrapper?> = arrayOfNulls(size)
    }
}

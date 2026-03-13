package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.source

data class LocalPathLookupExtendedIPCWrapper(
    val payload: List<LocalPathLookupExtended>,
) : Parcelable, IPCWrapper {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookupExtended::class.java.classLoader)!!
            .toList() as List<LocalPathLookupExtended>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathLookupExtendedIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupExtendedIPCWrapper =
            LocalPathLookupExtendedIPCWrapper(parcel)

        override fun newArray(size: Int): Array<LocalPathLookupExtendedIPCWrapper?> = arrayOfNulls(size)
    }
}


fun List<LocalPathLookupExtended>.toRemoteInputStream() =
    LocalPathLookupExtendedIPCWrapper(this).toSource().remoteInputStream()

fun RemoteInputStream.toLocalPathLookupExtended() = source().toIPCWrapper {
    LocalPathLookupExtendedIPCWrapper.createFromParcel(it)
}.payload
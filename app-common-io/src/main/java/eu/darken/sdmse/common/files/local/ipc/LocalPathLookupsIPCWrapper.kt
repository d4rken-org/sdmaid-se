package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.source

data class LocalPathLookupsIPCWrapper(
    val payload: List<LocalPathLookup>,
) : Parcelable, IPCWrapper {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookup::class.java.classLoader)!!.toList() as List<LocalPathLookup>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathLookupsIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupsIPCWrapper = LocalPathLookupsIPCWrapper(parcel)

        override fun newArray(size: Int): Array<LocalPathLookupsIPCWrapper?> = arrayOfNulls(size)
    }
}

fun List<LocalPathLookup>.toRemoteInputStream() = LocalPathLookupsIPCWrapper(this).toSource().remoteInputStream()

fun RemoteInputStream.toLocalPathLookups() = source().toIPCWrapper {
    LocalPathLookupsIPCWrapper.createFromParcel(it)
}.payload
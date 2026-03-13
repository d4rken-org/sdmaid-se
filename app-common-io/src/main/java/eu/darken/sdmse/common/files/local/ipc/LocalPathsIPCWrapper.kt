package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.source

data class LocalPathsIPCWrapper(
    val payload: List<LocalPath>,
) : Parcelable, IPCWrapper {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookup::class.java.classLoader)!!.toList() as List<LocalPath>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathsIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): LocalPathsIPCWrapper = LocalPathsIPCWrapper(parcel)

        override fun newArray(size: Int): Array<LocalPathsIPCWrapper?> = arrayOfNulls(size)
    }
}

fun List<LocalPath>.toRemoteInputStream() = LocalPathsIPCWrapper(this).toSource().remoteInputStream()

fun RemoteInputStream.toLocalPaths() = source().toIPCWrapper {
    LocalPathsIPCWrapper.createFromParcel(it)
}.payload
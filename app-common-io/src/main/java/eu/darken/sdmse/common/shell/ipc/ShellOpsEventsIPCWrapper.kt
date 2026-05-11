package eu.darken.sdmse.common.shell.ipc

import android.os.Parcel
import android.os.Parcelable

data class ShellOpsEventsIPCWrapper(
    val payload: List<ShellOpsStreamEvent>,
) : Parcelable {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(ShellOpsStreamEvent::class.java.classLoader)!!
            .toList() as List<ShellOpsStreamEvent>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShellOpsEventsIPCWrapper> {
        override fun createFromParcel(parcel: Parcel): ShellOpsEventsIPCWrapper =
            ShellOpsEventsIPCWrapper(parcel)

        override fun newArray(size: Int): Array<ShellOpsEventsIPCWrapper?> = arrayOfNulls(size)
    }
}

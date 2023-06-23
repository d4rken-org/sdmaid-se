package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.inputStream
import okio.Buffer

data class LocalPathLookupExtendedPayload(
    val payload: List<LocalPathLookupExtended>,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookupExtended::class.java.classLoader)!!
            .toList() as List<LocalPathLookupExtended>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathLookupExtendedPayload> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupExtendedPayload {

            return LocalPathLookupExtendedPayload(parcel)
        }

        override fun newArray(size: Int): Array<LocalPathLookupExtendedPayload?> = arrayOfNulls(size)
    }
}

fun LocalPathLookupExtendedPayload.toRemoteInputStream(): RemoteInputStream {
    val buffer = Buffer()
    val parcel = Parcel.obtain()
    this.writeToParcel(parcel, 0)
    buffer.write(parcel.marshall())
    parcel.recycle()
    buffer.flush()
    if (Bugs.isTrace) log(FileOpsHost.TAG, VERBOSE) { "Parcel marshalled and buffer flushed" }
    return buffer.remoteInputStream()
}

fun RemoteInputStream.toLocalPathLookupExtendedPayload(): LocalPathLookupExtendedPayload {
    val output = this.inputStream().readBytes()
    val parcel = Parcel.obtain()
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "Unmarshalling parcel now..." }
    parcel.unmarshall(output, 0, output.size)
    parcel.setDataPosition(0)
    val payload = LocalPathLookupExtendedPayload.createFromParcel(parcel)
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "Parcel unmarshalled, creating payload class..." }
    return payload
}

fun List<LocalPathLookupExtended>.toRemoteInputStream() = LocalPathLookupExtendedPayload(this).toRemoteInputStream()

fun RemoteInputStream.toLocalPathLookupExtended() = toLocalPathLookupExtendedPayload().payload
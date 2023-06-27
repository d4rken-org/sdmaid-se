package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.inputStream
import okio.Buffer

data class LocalPathLookupsPayload(
    val payload: List<LocalPathLookup>,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(LocalPathLookup::class.java.classLoader)!!.toList() as List<LocalPathLookup>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LocalPathLookupsPayload> {
        override fun createFromParcel(parcel: Parcel): LocalPathLookupsPayload {

            return LocalPathLookupsPayload(parcel)
        }

        override fun newArray(size: Int): Array<LocalPathLookupsPayload?> = arrayOfNulls(size)
    }
}

fun LocalPathLookupsPayload.toRemoteInputStream(): RemoteInputStream {
    val buffer = Buffer()
    val parcel = Parcel.obtain()
    this.writeToParcel(parcel, 0)
    buffer.write(parcel.marshall())
    parcel.recycle()
    buffer.flush()
    if (Bugs.isTrace) log(FileOpsHost.TAG, VERBOSE) { "Parcel marshalled and buffer flushed" }
    return buffer.remoteInputStream()
}

fun RemoteInputStream.toLocalPathLookupsPayload(): LocalPathLookupsPayload {
    val output = this.inputStream().readBytes()
    val parcel = Parcel.obtain()
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "Unmarshalling parcel now..." }
    parcel.unmarshall(output, 0, output.size)
    parcel.setDataPosition(0)
    val payload = LocalPathLookupsPayload.createFromParcel(parcel)
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "Parcel unmarshalled, creating payload class..." }
    return payload
}

fun List<LocalPathLookup>.toRemoteInputStream() = LocalPathLookupsPayload(this).toRemoteInputStream()

fun RemoteInputStream.toLocalPathLookups() = toLocalPathLookupsPayload().payload
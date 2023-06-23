package eu.darken.sdmse.common.pkgs.pkgops.ipc

import android.content.pm.PackageInfo
import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.inputStream
import okio.Buffer

data class PackageInfoPayload(
    val payload: List<PackageInfo>,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelableArray(PackageInfo::class.java.classLoader)!!.toList() as List<PackageInfo>
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelableArray(payload.toTypedArray(), flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PackageInfoPayload> {
        override fun createFromParcel(parcel: Parcel): PackageInfoPayload {

            return PackageInfoPayload(parcel)
        }

        override fun newArray(size: Int): Array<PackageInfoPayload?> = arrayOfNulls(size)
    }
}

fun PackageInfoPayload.toRemoteInputStream(): RemoteInputStream {
    val buffer = Buffer()
    val parcel = Parcel.obtain()
    this.writeToParcel(parcel, 0)
    buffer.write(parcel.marshall())
    parcel.recycle()
    buffer.flush()
    if (Bugs.isTrace) log(PkgOpsHost.TAG, VERBOSE) { "Parcel marshalled and buffer flushed" }
    return buffer.remoteInputStream()
}

fun RemoteInputStream.toLocalPathLookupsPayload(): PackageInfoPayload {
    val output = this.inputStream().readBytes()
    val parcel = Parcel.obtain()
    if (Bugs.isTrace) log(PkgOpsHost.TAG, VERBOSE) { "Unmarshalling parcel now..." }
    parcel.unmarshall(output, 0, output.size)
    parcel.setDataPosition(0)
    val payload = PackageInfoPayload.createFromParcel(parcel)
    if (Bugs.isTrace) log(PkgOpsHost.TAG, VERBOSE) { "Parcel unmarshalled, creating payload class..." }
    return payload
}

fun List<PackageInfo>.toRemoteInputStream() = PackageInfoPayload(this).toRemoteInputStream()

fun RemoteInputStream.toPackageInfos() = toLocalPathLookupsPayload().payload
package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import android.os.Parcelable
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.inputStream
import okio.Buffer
import okio.Source

interface IPCWrapper : Parcelable

fun IPCWrapper.toSource(): Source = Buffer().apply {
    val parcel = Parcel.obtain().apply { writeToParcel(this, 0) }
    write(parcel.marshall())
    parcel.recycle()
    flush()
    if (Bugs.isTrace) log(FileOpsHost.TAG, VERBOSE) { "Parcel marshalled and buffer flushed" }
}

fun <T : IPCWrapper> Source.toIPCWrapper(factory: (Parcel) -> T): T = inputStream().buffered().use {
    // Using OKIO buffer does not work, for some reason data is incomplete
    val output = it.readBytes()
    val parcel = Parcel.obtain()
    parcel.unmarshall(output, 0, output.size)
    parcel.setDataPosition(0)
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "Parcel unmarshalled, creating payload class..." }
    return factory(parcel)
}
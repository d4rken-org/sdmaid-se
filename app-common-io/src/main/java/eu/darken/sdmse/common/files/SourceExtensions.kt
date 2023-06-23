package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.remoteInputStream
import okio.Source
import okio.buffer
import java.io.InputStream

fun Source.inputStream(): InputStream {
    return buffer().inputStream()
}

fun Source.remoteInputStream(): RemoteInputStream {
    return inputStream().remoteInputStream()
}
package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.files.core.local.root.RemoteInputStream
import eu.darken.sdmse.common.files.core.local.root.remoteInputStream
import okio.Source
import okio.buffer
import java.io.InputStream

fun Source.inputStream(): InputStream {
    return buffer().inputStream()
}

fun Source.remoteInputStream(): RemoteInputStream {
    return inputStream().remoteInputStream()
}
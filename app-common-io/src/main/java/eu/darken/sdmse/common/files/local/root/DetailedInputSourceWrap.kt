package eu.darken.sdmse.common.files.local.root

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.remoteInputStream
import eu.darken.sdmse.common.root.io.RemoteInputStream
import okio.Source

data class DetailedInputSourceWrap(
    val path: LocalPath,
    val input: Source,
    val length: Long = -1
) : DetailedInputSource.Stub() {

    override fun path(): LocalPath = path

    override fun input(): RemoteInputStream = input.remoteInputStream()

    override fun length(): Long = length

}
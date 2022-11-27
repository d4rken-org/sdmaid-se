package eu.darken.sdmse.common.sharedresource

import java.io.Closeable

interface KeepAlive : Closeable {
    val isClosed: Boolean

    override fun close()
}
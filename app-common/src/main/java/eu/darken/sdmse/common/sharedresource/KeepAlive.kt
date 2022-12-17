package eu.darken.sdmse.common.sharedresource

import java.io.Closeable

interface KeepAlive : Closeable {
    val resourceId: String

    val isClosed: Boolean

    override fun close()
}
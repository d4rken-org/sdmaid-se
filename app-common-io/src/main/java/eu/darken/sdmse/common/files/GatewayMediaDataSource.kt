package eu.darken.sdmse.common.files

import android.media.MediaDataSource
import okio.FileHandle

/**
 * Wraps an okio [FileHandle] as an Android [MediaDataSource] for use with [android.media.MediaExtractor].
 * Supports seekable reads via the gateway abstraction, enabling media processing for files accessed
 * through any gateway (local, root, Shizuku, SAF).
 */
class GatewayMediaDataSource(private val handle: FileHandle) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        return handle.read(position, buffer, offset, size)
    }

    override fun getSize(): Long = handle.size()

    override fun close() = handle.close()
}

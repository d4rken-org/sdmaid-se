package eu.darken.sdmse.common.files

import okio.Buffer
import okio.Source
import okio.Timeout
import java.io.IOException

class ConstrainedSource constructor(
    private val parent: Source,
    byteLimit: Long
) : Source {

    private var closed: Boolean = false
    private var bytesRemaining: Long = byteLimit

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        check(!closed) { "ConstrainedSource is already closed!" }
        if (bytesRemaining == 0L) return -1

        val read = parent.read(sink, minOf(bytesRemaining, byteCount))
        if (read == -1L) throw IOException("unexpected end of stream, remaining=$bytesRemaining")
        bytesRemaining -= read
        return read
    }

    override fun timeout(): Timeout = parent.timeout()

    override fun close() {
        if (closed) return
        closed = true
    }
}

fun Source.constrain(byteLimit: Long): Source {
    return ConstrainedSource(this, byteLimit)
}
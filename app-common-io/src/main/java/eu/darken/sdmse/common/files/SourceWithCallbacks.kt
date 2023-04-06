package eu.darken.sdmse.common.files

import okio.Buffer
import okio.Source
import okio.Timeout

class SourceWithCallbacks(
    private val wrappedSource: Source,
    private val onPostClosed: () -> Unit
) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long = wrappedSource.read(sink, byteCount)

    override fun timeout(): Timeout = wrappedSource.timeout()

    override fun close() {
        try {
            wrappedSource.close()
        } finally {
            onPostClosed()
        }
    }

}

fun Source.callbacks(onPostClosed: () -> Unit): Source = SourceWithCallbacks(this, onPostClosed)
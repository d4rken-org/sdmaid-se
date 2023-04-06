package eu.darken.sdmse.common.files

import okio.Buffer
import okio.Sink
import okio.Timeout

class SinkWithCallbacks(
    private val wrappedSink: Sink,
    private val onPostClosed: () -> Unit
) : Sink {
    override fun flush() = wrappedSink.flush()

    override fun timeout(): Timeout = wrappedSink.timeout()

    override fun write(source: Buffer, byteCount: Long) = wrappedSink.write(source, byteCount)

    override fun close() {
        try {
            wrappedSink.close()
        } finally {
            onPostClosed()
        }
    }

}

fun Sink.callbacks(onPostClosed: () -> Unit): Sink = SinkWithCallbacks(this, onPostClosed)
package eu.darken.sdmse.appcontrol.core.export

import eu.darken.sdmse.common.compression.Zipper
import kotlinx.coroutines.ensureActive
import okio.BufferedSink
import okio.BufferedSource
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

/**
 * Copies chunk by chunk, checking [context] before each one.
 *
 * The copy itself is blocking, so a cancelled export would otherwise keep writing until the source
 * is through. Returns the number of bytes that were copied.
 */
internal fun copyChunked(source: BufferedSource, sink: BufferedSink, context: CoroutineContext): Long {
    val buffer = ByteArray(Zipper.BUFFER)
    var copied = 0L
    while (true) {
        context.ensureActive()
        val read = source.read(buffer, 0, buffer.size)
        if (read == -1) break
        sink.write(buffer, 0, read)
        copied += read
    }
    return copied
}

/** [copyChunked] for the stream based ZIP writing. */
internal fun copyChunked(input: InputStream, output: OutputStream, context: CoroutineContext): Long {
    val buffer = ByteArray(Zipper.BUFFER)
    var copied = 0L
    while (true) {
        context.ensureActive()
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        copied += read
    }
    return copied
}

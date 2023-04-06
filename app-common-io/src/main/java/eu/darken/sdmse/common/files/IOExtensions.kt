package eu.darken.sdmse.common.files

import okio.Sink
import okio.Source
import okio.buffer
import okio.sink
import java.io.Closeable
import java.io.File

fun Source.copyToAutoClose(output: Sink) {
    buffer().use { source ->
        output.buffer().use { dest ->
            dest.writeAll(source)
        }
    }
}

fun Source.copyToAutoClose(file: File) {
    copyToAutoClose(file.sink())
}

inline fun <T : Closeable?, R> T.useQuietly(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        try {
            this?.close()
        } catch (closeException: Throwable) {
            // cause.addSuppressed(closeException) // ignored here
        }
    }
}
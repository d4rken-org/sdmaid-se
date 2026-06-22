package eu.darken.sdmse.common.coil

import android.media.MediaDataSource
import coil.decode.ImageSource
import coil.fetch.MediaDataSourceFetcher.MediaSourceMetadata
import eu.darken.sdmse.common.files.callbacks
import okio.FileHandle
import okio.buffer
import java.io.File

suspend fun FileHandle.toImageSource(
    cacheDir: File,
): ImageSource {
    val handle = this
    // Wrap source so closing sourceBuffer also closes the FileHandle.
    // Standard image decoders only close the sourceBuffer, not the MediaDataSource metadata,
    // which previously caused FileHandle leaks ("A resource failed to call close").
    val sourceBuffer = this.source().callbacks { handle.close() }.buffer()
    val mediaDataSource = object : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            return handle.read(position, buffer, offset, size)
        }

        override fun getSize(): Long {
            return handle.size()
        }

        override fun close() {
            sourceBuffer.close()
        }
    }
    return ImageSource(
        source = sourceBuffer,
        cacheDirectory = cacheDir,
        metadata = MediaSourceMetadata(mediaDataSource),
    )
}
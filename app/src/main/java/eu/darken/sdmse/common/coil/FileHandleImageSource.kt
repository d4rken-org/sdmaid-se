package eu.darken.sdmse.common.coil

import android.media.MediaDataSource
import coil.annotation.ExperimentalCoilApi
import coil.decode.ImageSource
import coil.fetch.MediaDataSourceFetcher.MediaSourceMetadata
import okio.FileHandle
import okio.buffer
import java.io.File

@OptIn(ExperimentalCoilApi::class)
internal suspend fun FileHandle.toImageSource(
    cacheDir: File,
): ImageSource {
    val handle = this
    val sourceBuffer = this.source().buffer()
    val mediaDataSource = object : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            return handle.read(position, buffer, offset, size)
        }

        override fun getSize(): Long {
            return handle.size()
        }

        override fun close() {
            sourceBuffer.close()
            handle.close()
        }
    }
    return ImageSource(
        source = sourceBuffer,
        cacheDirectory = cacheDir,
        metadata = MediaSourceMetadata(mediaDataSource),
    )
}
package eu.darken.sdmse.common.coil

import android.media.MediaDataSource
import coil.annotation.ExperimentalCoilApi
import coil.decode.ImageSource
import coil.fetch.MediaDataSourceFetcher.MediaSourceMetadata
import coil.request.Options
import okio.FileHandle
import okio.buffer

@OptIn(ExperimentalCoilApi::class)
internal fun FileHandle.toImageSource(
    options: Options,
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
        context = options.context,
        metadata = MediaSourceMetadata(mediaDataSource),
    )
}
package eu.darken.sdmse.common.files

import okio.FileHandle

class FileHandleWithCallbacks(
    private val wrapped: FileHandle,
    private val onPostClosed: () -> Unit
) : FileHandle(wrapped.readWrite) {

    override fun protectedFlush() = wrapped.flush()

    override fun protectedResize(size: Long) = wrapped.resize(size)

    override fun protectedSize(): Long = wrapped.size()

    override fun protectedRead(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int
    ): Int = wrapped.read(fileOffset, array, arrayOffset, byteCount)

    override fun protectedWrite(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int
    ) = wrapped.write(fileOffset, array, arrayOffset, byteCount)

    override fun protectedClose() {
        try {
            wrapped.close()
        } finally {
            onPostClosed()
        }
    }
}

fun FileHandle.callbacks(onPostClosed: () -> Unit): FileHandle = FileHandleWithCallbacks(this, onPostClosed)
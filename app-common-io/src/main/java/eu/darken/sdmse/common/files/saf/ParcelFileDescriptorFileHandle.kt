package eu.darken.sdmse.common.files.saf

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import okio.FileHandle
import java.io.IOException

fun ParcelFileDescriptor.toFileHandle(readWrite: Boolean): FileHandle {
    return object : FileHandle(readWrite) {
        @Throws(IOException::class)
        override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int = try {
            val bytesRead = Os.pread(
                fileDescriptor,
                array,
                arrayOffset,
                byteCount,
                fileOffset
            )
            if (bytesRead == 0) -1 else bytesRead
        } catch (e: ErrnoException) {
            throw IOException("Error reading from file descriptor ${this@toFileHandle}", e)
        }

        @Throws(IOException::class)
        override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) {
            var totalBytesWritten = 0
            while (totalBytesWritten < byteCount) {
                try {
                    val bytesWritten = Os.pwrite(
                        fileDescriptor,
                        array,
                        arrayOffset + totalBytesWritten,
                        byteCount - totalBytesWritten,
                        fileOffset + totalBytesWritten
                    )
                    if (bytesWritten <= 0) throw IOException("Error writing to file descriptor, wrote 0 bytes")
                    totalBytesWritten += bytesWritten
                } catch (e: ErrnoException) {
                    throw IOException("Error writing to file descriptor", e)
                }
            }
        }

        @Throws(IOException::class)
        override fun protectedSize(): Long = try {
            Os.fstat(fileDescriptor).st_size
        } catch (e: ErrnoException) {
            throw IOException("Error getting file size ${this@toFileHandle}", e)
        }

        @Throws(IOException::class)
        override fun protectedResize(size: Long) = try {
            Os.ftruncate(fileDescriptor, size)
        } catch (e: ErrnoException) {
            throw IOException("Error resizing the file ${this@toFileHandle}", e)
        }

        @Throws(IOException::class)
        override fun protectedFlush() = try {
            Os.fsync(fileDescriptor)
        } catch (e: ErrnoException) {
            throw IOException("Error flushing file descriptor ${this@toFileHandle}", e)
        }

        @Throws(IOException::class)
        override fun protectedClose() = try {
            Os.close(fileDescriptor)
        } catch (e: ErrnoException) {
            throw IOException("Error closing file descriptor ${this@toFileHandle}", e)
        }
    }
}
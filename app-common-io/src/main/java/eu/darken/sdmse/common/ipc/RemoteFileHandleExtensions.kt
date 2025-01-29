package eu.darken.sdmse.common.ipc


import android.os.RemoteException
import eu.darken.sdmse.common.collections.toHex
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toOkioPath
import java.io.File


internal fun File.fileHandle(readWrite: Boolean): FileHandle {
    val okioPath = this.toOkioPath()
    return if (readWrite) {
        FileSystem.SYSTEM.openReadWrite(okioPath, mustCreate = false, mustExist = true)
    } else {
        FileSystem.SYSTEM.openReadOnly(okioPath)
    }
}

/**
 * Use this on the root side
 */
internal fun FileHandle.remoteFileHandle(): RemoteFileHandle.Stub = object : RemoteFileHandle.Stub() {

    override fun readWrite(): Boolean = this@remoteFileHandle.readWrite

    override fun read(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int = try {
        this@remoteFileHandle.read(fileOffset, array, arrayOffset, byteCount).also {
            if (Bugs.isTrace) {
                log(VERBOSE) { "read(rootside-p): $fileOffset, ${array.size}, $arrayOffset, $byteCount, read $it into ${array.toHex()}" }
            }
        }
    } catch (e: IOException) {
        log(ERROR) { "read() failed: ${e.asLog()}" }
        -2
    }

    override fun write(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) = try {
        this@remoteFileHandle.write(fileOffset, array, arrayOffset, byteCount)
    } catch (e: IOException) {
        log(ERROR) { "write() failed: ${e.asLog()}" }
    }

    override fun flush() = this@remoteFileHandle.flush()

    override fun resize(size: Long) = this@remoteFileHandle.resize(size)

    override fun size(): Long = try {
        this@remoteFileHandle.size()
    } catch (e: IOException) {
        log(ERROR) { "size() failed: ${e.asLog()}" }
        -2
    }

    override fun close() {
        this@remoteFileHandle.close()
    }

}

/**
 * Use this on the non-root side.
 */
internal fun RemoteFileHandle.fileHandle(readWrite: Boolean): FileHandle = object : FileHandle(readWrite) {

    @Throws(IOException::class)
    override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int = try {
        this@fileHandle.read(fileOffset, array, arrayOffset, byteCount).also {
            if (Bugs.isTrace) {
                log(VERBOSE) { "read(appside-p): $fileOffset, ${array.size}, $arrayOffset, $byteCount, read $it into ${array.toHex()}" }
            }
            if (it == -2) throw IOException("Remote Exception")
        }
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) = try {
        this@fileHandle.write(fileOffset, array, arrayOffset, byteCount)
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun protectedFlush() = try {
        this@fileHandle.flush()
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun protectedResize(size: Long) = try {
        this@fileHandle.resize(size)
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun protectedSize(): Long = try {
        this@fileHandle.size()
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun protectedClose() = try {
        this@fileHandle.close()
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

}
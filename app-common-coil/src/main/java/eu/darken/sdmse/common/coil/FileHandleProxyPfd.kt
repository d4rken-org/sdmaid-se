package eu.darken.sdmse.common.coil

import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import okio.FileHandle
import java.io.IOException

internal fun FileHandle.toProxyPfd(storageManager: StorageManager): ParcelFileDescriptor {
    val fileHandle = this
    val handlerThread = HandlerThread("coil-proxy-pfd").apply { start() }
    val handler = Handler(handlerThread.looper)

    val callback = object : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = try {
            fileHandle.size()
        } catch (e: IOException) {
            throw ErrnoException("onGetSize", OsConstants.EIO, e)
        }

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            val read = fileHandle.read(offset, data, 0, size)
            // ProxyFileDescriptorCallback interprets 0 as EOF
            return if (read == -1) 0 else read
        }

        override fun onRelease() {
            fileHandle.close()
            handlerThread.quitSafely()
        }
    }

    return try {
        storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            callback,
            handler,
        )
    } catch (e: Exception) {
        fileHandle.close()
        handlerThread.quitSafely()
        throw e
    }
}

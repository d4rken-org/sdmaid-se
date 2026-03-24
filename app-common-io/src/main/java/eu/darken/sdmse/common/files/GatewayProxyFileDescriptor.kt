package eu.darken.sdmse.common.files

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.FileHandle
import javax.inject.Inject

/**
 * Creates a [ParcelFileDescriptor] backed by an okio [FileHandle] via [StorageManager.openProxyFileDescriptor].
 * The system creates a virtual file descriptor via a FUSE-like proxy. When the consumer seeks and reads,
 * it calls our callback which delegates to the [FileHandle].
 *
 * This enables APIs that require a [java.io.FileDescriptor] (like [android.media.MediaMetadataRetriever])
 * to work with any file accessible through the gateway abstraction (local, root, Shizuku, SAF).
 *
 * No temp files. No copying. Seekable. Universal.
 */
class GatewayProxyFileDescriptor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val storageManager = context.getSystemService(StorageManager::class.java)!!
    private val handler = Handler(Looper.getMainLooper())

    fun create(handle: FileHandle): ParcelFileDescriptor {
        return storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            object : ProxyFileDescriptorCallback() {
                override fun onGetSize(): Long = handle.size()

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                    if (size == 0) return 0
                    return handle.read(offset, data, 0, size)
                }

                override fun onRelease() = handle.close()
            },
            handler,
        )
    }
}

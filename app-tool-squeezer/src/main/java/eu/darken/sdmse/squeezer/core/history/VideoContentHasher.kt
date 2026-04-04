package eu.darken.sdmse.squeezer.core.history

import dagger.Reusable
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.common.hashing.hash
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject

@Reusable
class VideoContentHasher @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun computePartialHash(path: APath): String = withContext(dispatcherProvider.IO) {
        val localPath = path as? LocalPath
        if (localPath != null) {
            computeLocalPartialHash(localPath)
        } else {
            gatewaySwitch.file(path, readWrite = false).source().hash(Hasher.Type.SHA256).format()
        }
    }

    private fun computeLocalPartialHash(path: LocalPath): String {
        val file = java.io.File(path.path)
        val fileSize = file.length()

        val md = MessageDigest.getInstance("SHA-256")

        if (fileSize < PARTIAL_THRESHOLD) {
            log(TAG) { "File < ${PARTIAL_THRESHOLD / 1024 / 1024}MB, using full hash: $path" }
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
        } else {
            log(TAG) { "Using partial hash for ${fileSize / 1024 / 1024}MB file: $path" }

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(CHUNK_SIZE.toInt())

                val firstRead = raf.read(buffer)
                if (firstRead > 0) md.update(buffer, 0, firstRead)

                raf.seek(fileSize - CHUNK_SIZE)
                val lastRead = raf.read(buffer)
                if (lastRead > 0) md.update(buffer, 0, lastRead)
            }

            val sizeBytes = ByteBuffer.allocate(8).putLong(fileSize).array()
            md.update(sizeBytes)
        }

        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CHUNK_SIZE = 1024L * 1024L // 1 MB
        private const val PARTIAL_THRESHOLD = 2L * CHUNK_SIZE // 2 MB
        private val TAG = logTag("Squeezer", "History", "VideoHasher")
    }
}

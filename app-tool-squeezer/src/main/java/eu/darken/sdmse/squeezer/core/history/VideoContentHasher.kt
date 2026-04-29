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
import eu.darken.sdmse.squeezer.core.ContentId
import eu.darken.sdmse.squeezer.core.ContentIdentifier
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher.Companion.CHUNK_SIZE
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher.Companion.PARTIAL_THRESHOLD
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Computes a stable fingerprint for a video file so the compression history can recognize
 * a file we've already processed across renames, moves, and filesystem-metadata changes.
 *
 * For files >= [PARTIAL_THRESHOLD] we hash the first and last [CHUNK_SIZE] bytes and mix
 * the total length into the digest. Full-video hashing is intentionally avoided: a typical
 * phone clip is 50–500 MB and a long recording can be multi-GB, so digesting the full
 * payload would stall every scan by minutes of I/O for no practical gain. The history is a
 * best-effort deduplication cache — a missed hit means we recompress a file we've already
 * touched, never a data-loss path.
 *
 * Collision surface: a partial hash could theoretically match two different videos that
 * share head, tail, and exact length. In practice the chance is negligible (1 MB head +
 * 1 MB tail + 8-byte length fed into SHA-256), and within a single user's scan candidate
 * set the risk is below any actionable threshold. If a collision ever occurred the
 * consequence is that one file is incorrectly skipped on a rescan, not corruption.
 */
@Reusable
class VideoContentHasher @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun computeHash(path: APath): ContentIdentifier.VideoHash = withContext(dispatcherProvider.IO) {
        val hash = computeRawHash(path)
        ContentIdentifier.VideoHash(ContentId(hash))
    }

    private suspend fun computeRawHash(path: APath): String {
        val localPath = path as? LocalPath
        return if (localPath != null) {
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

        init {
            require(PARTIAL_THRESHOLD >= CHUNK_SIZE) { "PARTIAL_THRESHOLD must be >= CHUNK_SIZE" }
        }
    }
}

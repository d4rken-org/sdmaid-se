package eu.darken.sdmse.common.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * On-disk encoding for backup files: gzip-compressed JSON.
 *
 * [decode] transparently falls back to plain UTF-8 when the bytes aren't gzipped, so older
 * uncompressed `.json` backups (and hand-authored files) still restore.
 */
object BackupCodec {

    // gzip magic number (RFC 1952).
    private const val GZIP_MAGIC_0 = 0x1f.toByte()
    private const val GZIP_MAGIC_1 = 0x8b.toByte()

    fun encode(json: String): ByteArray = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    fun decode(bytes: ByteArray): String = if (isGzip(bytes)) {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }.toString(Charsets.UTF_8)
    } else {
        bytes.toString(Charsets.UTF_8)
    }

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == GZIP_MAGIC_0 && bytes[1] == GZIP_MAGIC_1
}

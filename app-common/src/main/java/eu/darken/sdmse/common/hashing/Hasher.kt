package eu.darken.sdmse.common.hashing

import okio.ByteString.Companion.toByteString
import okio.Source
import okio.buffer
import java.security.MessageDigest

class Hasher(
    private val type: Type,
) {

    fun calc(source: Source): Result = MessageDigest
        .getInstance(type.code)
        .let { md ->
            source.buffer().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest()
        }
        .let { Result(type, it) }

    data class Result(
        val type: Type,
        val hash: ByteArray,
    ) {
        fun formatAs(format: Format): String = when (format) {
            Format.HEX -> hash.joinToString(separator = "") { String.format("%02X", it) }.lowercase()
            Format.BASE64 -> hash.toByteString().base64()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false

            if (type != other.type) return false
            if (!hash.contentEquals(other.hash)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + hash.contentHashCode()
            return result
        }

        enum class Format {
            HEX, BASE64
        }
    }

    enum class Type(val code: String) {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        ;
    }
}
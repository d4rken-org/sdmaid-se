package eu.darken.sdmse.common.hashing

import okio.ByteString
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
        .let { Result(type, it.toByteString(0, it.size)) }

    data class Result(
        val type: Type,
        val hash: ByteString,
    ) {
        fun formatAs(format: Format): String = when (format) {
            Format.HEX -> hash.hex()
            Format.BASE64 -> hash.base64()
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
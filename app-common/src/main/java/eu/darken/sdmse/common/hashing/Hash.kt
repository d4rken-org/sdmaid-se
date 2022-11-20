package eu.darken.sdmse.common.hashing

import eu.darken.sdmse.common.hashing.Hash.Format.BASE64
import eu.darken.sdmse.common.hashing.Hash.Format.HEX
import okio.ByteString.Companion.toByteString
import java.io.File
import java.security.MessageDigest

class Hash(
    private val type: Algo,
    private val format: Format = HEX
) {

    fun calc(string: String) = calc(string.toByteArray())

    fun calc(data: ByteArray): Result = MessageDigest
        .getInstance(type.code)
        .digest(data)
        .formatHash(format)
        .let { Result(it) }

    fun calc(file: File) {
        MessageDigest
            .getInstance(type.code)
            .let { md ->
                file.inputStream().use { stream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (stream.read(buffer).also { read = it } > 0) {
                        md.update(buffer, 0, read)
                    }
                }
                md.digest()
            }
            .formatHash(format)
            .let { Result(it) }
    }


    private fun ByteArray.formatHash(format: Format): String = when (format) {
        HEX -> this.joinToString(separator = "") { String.format("%02X", it) }.lowercase()
        BASE64 -> this.toByteString().base64()
    }

    data class Result(
        val hash: String,
    )

    enum class Format {
        HEX, BASE64
    }

    enum class Algo(val code: String) {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        ;
    }
}

fun ByteArray.toHash(
    type: Hash.Algo,
    format: Hash.Format = HEX
): String = Hash(type, format).calc(this).hash

fun String.toHash(
    type: Hash.Algo,
    format: Hash.Format = HEX
): String = Hash(type, format).calc(this).hash

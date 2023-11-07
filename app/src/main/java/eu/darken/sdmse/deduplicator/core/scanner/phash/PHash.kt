package eu.darken.sdmse.deduplicator.core.scanner.phash

import okio.ByteString

class PHash {


    data class Result(
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
}
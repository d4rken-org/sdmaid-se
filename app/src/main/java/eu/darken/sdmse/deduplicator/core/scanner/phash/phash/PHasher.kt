package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import android.graphics.Bitmap

class PHasher {

    fun calc(source: Bitmap): Result {
        val hash = PHashAlgorithm().calc(source)

        return Result(
            hash = hash
        )
    }

    data class Result(
        val hash: Long,
    ) {

        fun similarityTo(other: Result): Double {
            val similarityMask = (hash or other.hash and (hash and other.hash).inv()).inv()
            return similarityMask.countOneBits() / Long.SIZE_BITS.toDouble()
        }

        fun format(format: Format = Format.BINARY): String = when (format) {
            Format.BINARY -> hash.toString(2).padStart(64, '0')
        }

        enum class Format {
            BINARY,
            ;
        }
    }
}
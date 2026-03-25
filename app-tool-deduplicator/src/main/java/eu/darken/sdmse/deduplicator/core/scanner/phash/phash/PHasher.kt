package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import android.graphics.Bitmap

class PHasher {

    private val algorithm = PHashAlgorithm()

    fun calc(source: Bitmap): Result {
        val raw = algorithm.calc(source)

        return Result(
            hash = raw.hash,
            acVariance = raw.acVariance,
        )
    }

    data class Result(
        val hash: PHashBits = PHashBits(0L),
        val acVariance: Double = 0.0,
    ) {

        fun similarityTo(other: Result): Double = hash.similarityTo(other.hash)

        fun format(format: Format = Format.BINARY): String = when (format) {
            Format.BINARY -> hash.toString()
        }

        enum class Format {
            BINARY,
            ;
        }
    }
}

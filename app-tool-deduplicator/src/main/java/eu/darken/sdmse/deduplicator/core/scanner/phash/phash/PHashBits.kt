package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

/**
 * Variable-length perceptual hash stored as packed bits in a [LongArray].
 * Bits are packed MSB-first: bit 0 is the highest bit of words[0].
 */
class PHashBits(words: LongArray, val size: Int) {
    private val words: LongArray

    init {
        require(size > 0) { "size must be > 0" }
        require(words.size == (size + Long.SIZE_BITS - 1) / Long.SIZE_BITS) {
            "words.size ${words.size} doesn't match size $size"
        }
        this.words = words.copyOf()
        val unusedBits = this.words.size * Long.SIZE_BITS - size
        if (unusedBits > 0) {
            this.words[this.words.lastIndex] =
                this.words[this.words.lastIndex] and ((-1L) shl unusedBits)
        }
    }

    constructor(hash: Long) : this(longArrayOf(hash), Long.SIZE_BITS)

    fun similarityTo(other: PHashBits): Double {
        require(size == other.size) { "Hash size mismatch: $size vs ${other.size}" }
        var matchingBits = 0
        for (i in words.indices) {
            matchingBits += (words[i] xor other.words[i]).inv().countOneBits()
        }
        val unusedBits = words.size * Long.SIZE_BITS - size
        matchingBits -= unusedBits
        return matchingBits.toDouble() / size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PHashBits) return false
        return size == other.size && words.contentEquals(other.words)
    }

    override fun hashCode(): Int = words.contentHashCode() * 31 + size

    override fun toString(): String = words.joinToString("") {
        it.toULong().toString(2).padStart(Long.SIZE_BITS, '0')
    }.take(size)
}

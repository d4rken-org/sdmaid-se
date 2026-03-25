package eu.darken.sdmse.deduplicator.core.scanner.phash.phash

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PHashBitsTest : BaseTest() {

    @Test
    fun `identical hashes have similarity 1`() {
        val a = PHashBits(0x123456789ABCDEF0L)
        val b = PHashBits(0x123456789ABCDEF0L)
        a.similarityTo(b) shouldBe 1.0
    }

    @Test
    fun `opposite hashes have similarity 0`() {
        val a = PHashBits(-1L) // all 1s
        val b = PHashBits(0L) // all 0s
        a.similarityTo(b) shouldBe 0.0
    }

    @Test
    fun `similarity is symmetric`() {
        val a = PHashBits(0x123456780000000L)
        val b = PHashBits(0x000000009ABCDEF0L)
        a.similarityTo(b) shouldBe b.similarityTo(a)
    }

    @Test
    fun `similarity is between 0 and 1`() {
        val a = PHashBits(0x0F0F0F0F0F0F0F0FL)
        val b = PHashBits(0x00FF00FF00FF00FFL)
        val sim = a.similarityTo(b)
        sim shouldBeGreaterThan -0.01
        sim shouldBeLessThan 1.01
    }

    @Test
    fun `multi-word hash - identical`() {
        val words = longArrayOf(0x123456789ABCDEF0L, 0x1234567800000000L)
        val a = PHashBits(words, 121)
        val b = PHashBits(words.copyOf(), 121)
        a.similarityTo(b) shouldBe 1.0
    }

    @Test
    fun `multi-word hash - cross word boundary`() {
        // Set bit 63 (last bit of word 0) in one, bit 64 (first bit of word 1) in other
        val wordsA = longArrayOf(1L, 0L) // bit 63 set
        val wordsB = longArrayOf(0L, Long.MIN_VALUE) // bit 64 set (MSB of word 1)
        val a = PHashBits(wordsA, 121)
        val b = PHashBits(wordsB, 121)
        // 119 bits match (all zero), 2 bits differ
        val expected = 119.0 / 121.0
        a.similarityTo(b) shouldBe expected
    }

    @Test
    fun `trailing bits are masked`() {
        // 121 bits = 2 Longs, 7 unused trailing bits
        val wordsWithTrash = longArrayOf(0L, 0x7FL) // 7 trailing bits set (should be masked)
        val wordsClean = longArrayOf(0L, 0L)
        val a = PHashBits(wordsWithTrash, 121)
        val b = PHashBits(wordsClean, 121)
        a shouldBe b
        a.similarityTo(b) shouldBe 1.0
    }

    @Test
    fun `size mismatch throws`() {
        val a = PHashBits(0L)
        val b = PHashBits(longArrayOf(0L, 0L), 121)
        shouldThrow<IllegalArgumentException> {
            a.similarityTo(b)
        }
    }

    @Test
    fun `equals and hashCode`() {
        val a = PHashBits(longArrayOf(0xABCDL, 0L), 121)
        val b = PHashBits(longArrayOf(0xABCDL, 0L), 121)
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
    }

    @Test
    fun `toString produces correct length`() {
        val a = PHashBits(longArrayOf(-1L, -1L), 121)
        a.toString().length shouldBe 121
    }

    @Test
    fun `invalid size throws`() {
        shouldThrow<IllegalArgumentException> {
            PHashBits(longArrayOf(), 0)
        }
    }

    @Test
    fun `mismatched words size throws`() {
        shouldThrow<IllegalArgumentException> {
            PHashBits(longArrayOf(0L, 0L, 0L), 121) // needs 2, got 3
        }
    }
}

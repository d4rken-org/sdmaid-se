package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FingerprintCalculatorResultTest : BaseTest() {

    private fun result(vararg longs: Long) = FingerprintCalculator.Result(fingerprint = longArrayOf(*longs))

    @Test
    fun `same fingerprint returns 1_0`() {
        val a = result(0x1234567890ABCDEFL, 0x0EDCBA0987654321L, 0x1111111111111111L, 0x2222222222222222L)
        val b = result(0x1234567890ABCDEFL, 0x0EDCBA0987654321L, 0x1111111111111111L, 0x2222222222222222L)
        a.similarityTo(b) shouldBe 1.0
    }

    @Test
    fun `all zeros vs all ones returns 0_0`() {
        val zeros = result(0L, 0L, 0L, 0L)
        val ones = result(-1L, -1L, -1L, -1L) // -1L = all bits set
        zeros.similarityTo(ones) shouldBe 0.0
    }

    @Test
    fun `one bit different returns near perfect similarity`() {
        val a = result(0L, 0L, 0L, 0L)
        val b = result(1L, 0L, 0L, 0L) // 1 bit different out of 256
        val expected = 255.0 / 256.0
        a.similarityTo(b) shouldBe expected
    }

    @Test
    fun `half bits different returns approximately 0_5`() {
        // 0x5555... has alternating bits: 01010101...
        val a = result(0x5555555555555555L, 0x5555555555555555L, 0x5555555555555555L, 0x5555555555555555L)
        val b = result(0x5555555555555555L.inv(), 0x5555555555555555L.inv(), 0x5555555555555555L.inv(), 0x5555555555555555L.inv())
        a.similarityTo(b) shouldBe 0.0 // These are exact inverses
    }

    @Test
    fun `similarity is symmetric`() {
        val a = result(0x123456789L, 0xABCDEF0L, 0x111L, 0x222L)
        val b = result(0x987654321L, 0xFEDCBA0L, 0x333L, 0x444L)
        a.similarityTo(b) shouldBe b.similarityTo(a)
    }

    @Test
    fun `equals and hashCode with same content`() {
        val a = result(1L, 2L, 3L, 4L)
        val b = result(1L, 2L, 3L, 4L)
        (a == b) shouldBe true
        a.hashCode() shouldBe b.hashCode()
    }

    @Test
    fun `equals with different content`() {
        val a = result(1L, 2L, 3L, 4L)
        val b = result(1L, 2L, 3L, 5L)
        (a == b) shouldBe false
    }

    @Test
    fun `similarity is between 0 and 1`() {
        val a = result(0x1234L, 0x5678L, 0x9ABCL, 0xDEF0L)
        val b = result(0xFEDCL, 0xBA98L, 0x7654L, 0x3210L)
        val sim = a.similarityTo(b)
        sim shouldBeGreaterThan -0.001
        sim shouldBeLessThan 1.001
    }
}

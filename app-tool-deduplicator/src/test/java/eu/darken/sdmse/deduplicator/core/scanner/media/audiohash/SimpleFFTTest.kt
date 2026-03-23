package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class SimpleFFTTest : BaseTest() {

    private fun create() = SimpleFFT()

    @Test
    fun `sine wave produces peak at correct bin`() {
        val n = 512
        val sampleRate = 512.0 // 1 Hz per bin for easy math
        val targetFreq = 10.0 // Should peak at bin 10
        val real = DoubleArray(n) { i -> sin(2.0 * PI * targetFreq * i / sampleRate) }
        val imag = DoubleArray(n)

        create().fft(real, imag)
        val magnitude = create().magnitudeSpectrum(real, imag)

        // Find the bin with maximum magnitude
        val peakBin = magnitude.indices.maxByOrNull { magnitude[it] }!!
        peakBin shouldBe 10

        // Peak should be significantly larger than other bins
        val peakMag = magnitude[peakBin]
        val avgOther = magnitude.filterIndexed { i, _ -> i != peakBin }.average()
        peakMag shouldBeGreaterThan avgOther * 10
    }

    @Test
    fun `DC signal has energy only in bin 0`() {
        val n = 256
        val real = DoubleArray(n) { 1.0 }
        val imag = DoubleArray(n)

        create().fft(real, imag)
        val magnitude = create().magnitudeSpectrum(real, imag)

        magnitude[0] shouldBeGreaterThan 0.0
        for (i in 1 until magnitude.size) {
            magnitude[i] shouldBeLessThan 0.001
        }
    }

    @Test
    fun `zero input produces zero output`() {
        val n = 128
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        create().fft(real, imag)
        val magnitude = create().magnitudeSpectrum(real, imag)

        magnitude.forEach { it shouldBe 0.0 }
    }

    @Test
    fun `parseval theorem - energy conservation`() {
        val n = 256
        val real = DoubleArray(n) { sin(2.0 * PI * 7.0 * it / n) + 0.5 * sin(2.0 * PI * 23.0 * it / n) }
        val imag = DoubleArray(n)

        val timeEnergy = real.sumOf { it * it }

        create().fft(real, imag)
        val freqEnergy = (0 until n).sumOf { real[it] * real[it] + imag[it] * imag[it] } / n

        // Should be approximately equal (within floating point tolerance)
        val ratio = timeEnergy / freqEnergy
        ratio shouldBeGreaterThan 0.99
        ratio shouldBeLessThan 1.01
    }

    @Test
    fun `non-power-of-2 throws`() {
        shouldThrow<IllegalArgumentException> {
            create().fft(DoubleArray(100), DoubleArray(100))
        }
    }

    @Test
    fun `empty array throws`() {
        shouldThrow<IllegalArgumentException> {
            create().fft(DoubleArray(0), DoubleArray(0))
        }
    }

    @Test
    fun `mismatched array sizes throws`() {
        shouldThrow<IllegalArgumentException> {
            create().fft(DoubleArray(128), DoubleArray(256))
        }
    }

    @Test
    fun `size 1 works`() {
        val real = doubleArrayOf(42.0)
        val imag = doubleArrayOf(0.0)

        create().fft(real, imag)

        real[0] shouldBe 42.0
        imag[0] shouldBe 0.0
    }

    @Test
    fun `magnitudeSpectrum returns half length`() {
        val n = 512
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        create().fft(real, imag)

        val magnitude = create().magnitudeSpectrum(real, imag)
        magnitude.size shouldBe 256
    }

    @Test
    fun `magnitude is computed correctly`() {
        val real = doubleArrayOf(3.0, 0.0)
        val imag = doubleArrayOf(4.0, 0.0)

        val magnitude = create().magnitudeSpectrum(real, imag)
        magnitude.size shouldBe 1
        magnitude[0] shouldBe 5.0 // sqrt(3^2 + 4^2) = 5
    }
}

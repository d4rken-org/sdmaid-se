package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal radix-2 Cooley-Tukey FFT for power-of-2 sizes.
 * Operates in-place on interleaved real/imaginary arrays.
 */
class SimpleFFT @Inject constructor() {

    /**
     * Compute the FFT of [real] and [imag] arrays in-place.
     * Both arrays must have the same power-of-2 length.
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n > 0 && n and (n - 1) == 0) { "Length must be a power of 2, got $n" }
        require(real.size == imag.size) { "Real and imaginary arrays must have the same length" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Cooley-Tukey butterfly
        var step = 2
        while (step <= n) {
            val halfStep = step / 2
            val angle = -2.0 * PI / step
            for (i in 0 until n step step) {
                for (m in 0 until halfStep) {
                    val w = angle * m
                    val wr = cos(w)
                    val wi = sin(w)
                    val idx1 = i + m
                    val idx2 = i + m + halfStep
                    val tr = wr * real[idx2] - wi * imag[idx2]
                    val ti = wr * imag[idx2] + wi * real[idx2]
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] += tr
                    imag[idx1] += ti
                }
            }
            step *= 2
        }
    }

    /**
     * Compute the magnitude spectrum from [real] and [imag] after FFT.
     * Returns only the first half (positive frequencies).
     */
    fun magnitudeSpectrum(real: DoubleArray, imag: DoubleArray): DoubleArray {
        val half = real.size / 2
        return DoubleArray(half) { i ->
            kotlin.math.sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
    }
}

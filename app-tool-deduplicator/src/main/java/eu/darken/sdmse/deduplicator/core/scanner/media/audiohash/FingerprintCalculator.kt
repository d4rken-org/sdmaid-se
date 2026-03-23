package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln

/**
 * Computes a 256-bit audio fingerprint from mono PCM samples.
 *
 * Algorithm:
 * 1. Split into overlapping frames (512 samples, 256 hop)
 * 2. Apply Hann window + FFT per frame
 * 3. Compute energy in 8 logarithmic frequency bands
 * 4. For consecutive frame pairs, threshold energy differences → 1 bit per band
 * 5. Sample uniformly to produce a fixed 256-bit fingerprint
 */
class FingerprintCalculator @Inject constructor(
    private val fft: SimpleFFT,
) {

    /**
     * Compute a 256-bit fingerprint from mono PCM samples at [sampleRate] Hz.
     *
     * @param samples Mono PCM samples as ShortArray
     * @param sampleRate Sample rate in Hz
     * @return Fingerprint result, or null if too few samples
     */
    fun calculate(samples: ShortArray, sampleRate: Int): Result? {
        val monoSamples = if (sampleRate != TARGET_SAMPLE_RATE) {
            downsample(samples, sampleRate, TARGET_SAMPLE_RATE)
        } else {
            samples
        }

        // Need at least 2 frames for frame-pair differencing
        if (monoSamples.size < FRAME_SIZE + HOP_SIZE) return null

        val bandEnergies = extractBandEnergies(monoSamples)
        if (bandEnergies.size < 2) return null

        val rawBits = computeEnergyDifferenceBits(bandEnergies)
        val fingerprint = sampleToFixedLength(rawBits)

        return Result(fingerprint = fingerprint)
    }

    private fun extractBandEnergies(samples: ShortArray): List<DoubleArray> {
        val energies = mutableListOf<DoubleArray>()
        var offset = 0

        while (offset + FRAME_SIZE <= samples.size) {
            val real = DoubleArray(FRAME_SIZE) { i -> samples[offset + i].toDouble() * hannWindow[i] }
            val imag = DoubleArray(FRAME_SIZE)

            fft.fft(real, imag)
            val magnitude = fft.magnitudeSpectrum(real, imag)

            val bands = DoubleArray(NUM_BANDS) { band ->
                val start = bandBoundaries[band]
                val end = bandBoundaries[band + 1]
                var energy = 0.0
                for (bin in start until end) {
                    energy += magnitude[bin] * magnitude[bin]
                }
                energy
            }

            energies.add(bands)
            offset += HOP_SIZE
        }

        return energies
    }

    private fun computeEnergyDifferenceBits(bandEnergies: List<DoubleArray>): BooleanArray {
        val numPairs = bandEnergies.size - 1
        val bits = BooleanArray(numPairs * NUM_BANDS)

        for (i in 0 until numPairs) {
            for (band in 0 until NUM_BANDS) {
                bits[i * NUM_BANDS + band] = bandEnergies[i + 1][band] > bandEnergies[i][band]
            }
        }

        return bits
    }

    private fun sampleToFixedLength(bits: BooleanArray): LongArray {
        val fingerprint = LongArray(FINGERPRINT_LONGS)

        for (i in 0 until FINGERPRINT_BITS) {
            // Map fingerprint bit position to source bit via uniform sampling
            val srcIndex = (i.toLong() * bits.size / FINGERPRINT_BITS).toInt()
                .coerceIn(0, bits.size - 1)

            if (bits[srcIndex]) {
                val longIndex = i / Long.SIZE_BITS
                val bitIndex = i % Long.SIZE_BITS
                fingerprint[longIndex] = fingerprint[longIndex] or (1L shl bitIndex)
            }
        }

        return fingerprint
    }

    private fun downsample(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate <= toRate) return samples
        val ratio = fromRate.toDouble() / toRate
        val outSize = (samples.size / ratio).toInt()
        return ShortArray(outSize) { i ->
            samples[(i * ratio).toInt().coerceIn(0, samples.size - 1)]
        }
    }

    data class Result(
        val fingerprint: LongArray,
    ) {

        fun similarityTo(other: Result): Double {
            var matchingBits = 0
            var totalBits = 0
            for (i in fingerprint.indices) {
                val xor = fingerprint[i] xor other.fingerprint[i]
                matchingBits += (Long.SIZE_BITS - xor.countOneBits())
                totalBits += Long.SIZE_BITS
            }
            return matchingBits.toDouble() / totalBits
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false
            return fingerprint.contentEquals(other.fingerprint)
        }

        override fun hashCode(): Int = fingerprint.contentHashCode()
    }

    companion object {
        private const val FRAME_SIZE = 512
        private const val HOP_SIZE = 256
        private const val NUM_BANDS = 8
        private const val FINGERPRINT_BITS = 256
        private const val FINGERPRINT_LONGS = FINGERPRINT_BITS / Long.SIZE_BITS // 4
        const val TARGET_SAMPLE_RATE = 8000

        // Pre-computed Hann window coefficients
        private val hannWindow = DoubleArray(FRAME_SIZE) { i ->
            0.5 * (1.0 - cos(2.0 * PI * i / (FRAME_SIZE - 1)))
        }

        // Logarithmically-spaced band boundaries for 256 FFT bins (half of 512)
        private val bandBoundaries: IntArray = run {
            val halfSize = FRAME_SIZE / 2
            val boundaries = IntArray(NUM_BANDS + 1)
            boundaries[0] = 1 // Skip DC
            boundaries[NUM_BANDS] = halfSize
            val logMin = ln(1.0)
            val logMax = ln(halfSize.toDouble())
            for (i in 1 until NUM_BANDS) {
                boundaries[i] = kotlin.math.exp(logMin + (logMax - logMin) * i / NUM_BANDS).toInt()
            }
            boundaries
        }
    }
}

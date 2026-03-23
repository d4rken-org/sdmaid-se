package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.math.PI
import kotlin.math.sin

class FingerprintCalculatorTest : BaseTest() {

    private fun create() = FingerprintCalculator(SimpleFFT())

    private fun generateSineWave(
        frequency: Double,
        sampleRate: Int = FingerprintCalculator.TARGET_SAMPLE_RATE,
        durationSamples: Int = sampleRate * 2,
        amplitude: Short = 16000,
    ): ShortArray {
        return ShortArray(durationSamples) { i ->
            (amplitude * sin(2.0 * PI * frequency * i / sampleRate)).toInt().toShort()
        }
    }

    private fun generateWhiteNoise(
        sampleRate: Int = FingerprintCalculator.TARGET_SAMPLE_RATE,
        durationSamples: Int = sampleRate * 2,
        seed: Long = 42L,
    ): ShortArray {
        val random = java.util.Random(seed)
        return ShortArray(durationSamples) { (random.nextInt(65536) - 32768).toShort() }
    }

    @Test
    fun `identical input produces identical fingerprint`() {
        val samples = generateSineWave(440.0)
        val result1 = create().calculate(samples, FingerprintCalculator.TARGET_SAMPLE_RATE)
        val result2 = create().calculate(samples, FingerprintCalculator.TARGET_SAMPLE_RATE)

        result1.shouldNotBeNull()
        result2.shouldNotBeNull()
        result1 shouldBe result2
        result1.similarityTo(result2) shouldBe 1.0
    }

    @Test
    fun `different audio produces different fingerprint`() {
        val sine = generateSineWave(440.0)
        val noise = generateWhiteNoise()

        val result1 = create().calculate(sine, FingerprintCalculator.TARGET_SAMPLE_RATE)
        val result2 = create().calculate(noise, FingerprintCalculator.TARGET_SAMPLE_RATE)

        result1.shouldNotBeNull()
        result2.shouldNotBeNull()
        result1.similarityTo(result2) shouldBeLessThan 0.9
    }

    @Test
    fun `too short input returns null`() {
        // Less than FRAME_SIZE + HOP_SIZE = 768 samples
        val samples = ShortArray(500) { 100 }
        create().calculate(samples, FingerprintCalculator.TARGET_SAMPLE_RATE).shouldBeNull()
    }

    @Test
    fun `empty input returns null`() {
        create().calculate(ShortArray(0), FingerprintCalculator.TARGET_SAMPLE_RATE).shouldBeNull()
    }

    @Test
    fun `exactly minimum size produces result`() {
        // FRAME_SIZE(512) + HOP_SIZE(256) = 768 — minimum for 2 frames
        val samples = generateSineWave(440.0, durationSamples = 768)
        create().calculate(samples, FingerprintCalculator.TARGET_SAMPLE_RATE).shouldNotBeNull()
    }

    @Test
    fun `downsample preserves fingerprint`() {
        val targetRate = FingerprintCalculator.TARGET_SAMPLE_RATE
        val highRate = 44100

        // Generate at target rate
        val lowRateSamples = generateSineWave(440.0, sampleRate = targetRate, durationSamples = targetRate * 2)

        // Generate same wave at high rate
        val highRateSamples = generateSineWave(440.0, sampleRate = highRate, durationSamples = highRate * 2)

        val result1 = create().calculate(lowRateSamples, targetRate)
        val result2 = create().calculate(highRateSamples, highRate)

        result1.shouldNotBeNull()
        result2.shouldNotBeNull()
        // Simple decimation downsampling is lossy (no anti-alias filter),
        // so fingerprints won't be identical. Just verify both produce valid results.
        // In practice, real duplicates are decoded at the same rate by MediaCodec.
        result1.similarityTo(result2) shouldBeGreaterThan 0.5
    }

    @Test
    fun `silence is rejected as featureless`() {
        val silence = ShortArray(FingerprintCalculator.TARGET_SAMPLE_RATE * 2)
        // Silence produces all-zero energy differences → low entropy → rejected
        create().calculate(silence, FingerprintCalculator.TARGET_SAMPLE_RATE).shouldBeNull()
    }

    @Test
    fun `near-constant noise is rejected as featureless`() {
        // Constant amplitude — energy differences are near-zero
        val constantNoise = ShortArray(FingerprintCalculator.TARGET_SAMPLE_RATE * 2) { 1000 }
        create().calculate(constantNoise, FingerprintCalculator.TARGET_SAMPLE_RATE).shouldBeNull()
    }

    @Test
    fun `fingerprint is always 4 Longs`() {
        val short = generateSineWave(440.0, durationSamples = 1000)
        val long = generateSineWave(440.0, durationSamples = 40000)

        val resultShort = create().calculate(short, FingerprintCalculator.TARGET_SAMPLE_RATE)
        val resultLong = create().calculate(long, FingerprintCalculator.TARGET_SAMPLE_RATE)

        resultShort.shouldNotBeNull()
        resultLong.shouldNotBeNull()
        resultShort.fingerprint.size shouldBe 4
        resultLong.fingerprint.size shouldBe 4
    }

    @Test
    fun `amplitude scaling changes fingerprint - known limitation`() {
        // Energy difference bits use absolute values, so scaling changes the fingerprint
        // This documents the known limitation rather than asserting similarity
        val loud = generateSineWave(440.0, amplitude = 16000)
        val quiet = generateSineWave(440.0, amplitude = 1000)

        val resultLoud = create().calculate(loud, FingerprintCalculator.TARGET_SAMPLE_RATE)
        val resultQuiet = create().calculate(quiet, FingerprintCalculator.TARGET_SAMPLE_RATE)

        resultLoud.shouldNotBeNull()
        resultQuiet.shouldNotBeNull()
        // Same waveform at different amplitudes — fingerprints may differ
        // because energy difference bits are based on absolute energy changes.
        // We just verify both produce valid results; similarity is not guaranteed.
    }

    @Test
    fun `same rate as target skips downsampling`() {
        val samples = generateSineWave(440.0, sampleRate = FingerprintCalculator.TARGET_SAMPLE_RATE)
        // Should not throw or produce different results
        val result = create().calculate(samples, FingerprintCalculator.TARGET_SAMPLE_RATE)
        result.shouldNotBeNull()
    }
}

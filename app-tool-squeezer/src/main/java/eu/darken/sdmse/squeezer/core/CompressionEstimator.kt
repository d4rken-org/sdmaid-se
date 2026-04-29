package eu.darken.sdmse.squeezer.core

import dagger.Reusable
import javax.inject.Inject

@Reusable
class CompressionEstimator @Inject constructor() {

    fun estimateCompressedSize(originalSize: Long, mimeType: String, quality: Int): Long? {
        val ratio = estimateOutputRatio(mimeType, quality) ?: return null
        return (originalSize * ratio).toLong()
    }

    fun estimateOutputRatio(mimeType: String, quality: Int): Double? {
        return when (mimeType) {
            CompressibleImage.MIME_TYPE_JPEG -> {
                when {
                    quality == 100 -> 1.0
                    quality <= 50 -> 0.30
                    quality <= 70 -> 0.50
                    quality <= 80 -> 0.65
                    quality <= 90 -> 0.80
                    else -> 0.90
                }
            }
            CompressibleImage.MIME_TYPE_WEBP -> {
                when {
                    quality == 100 -> 1.0
                    quality <= 50 -> 0.25
                    quality <= 70 -> 0.40
                    quality <= 80 -> 0.55
                    quality <= 90 -> 0.70
                    else -> 0.85
                }
            }
            else -> null
        }
    }

    /**
     * Video size estimate using target bitrate × duration instead of a ratio.
     *
     * The video pipeline sets target bitrate linearly from the quality slider, so the right
     * estimator is also linear in bitrate. A fixed allowance is added for audio + container
     * muxing overhead (~AUDIO_BITRATE_BPS × duration), which is roughly constant across files
     * with typical audio tracks.
     *
     * Returns null if duration or bitrate are unavailable — the UI should render this as
     * "unknown" rather than showing a misleading zero.
     */
    fun estimateVideoSize(
        originalSize: Long,
        durationMs: Long,
        originalBitrateBps: Long,
        quality: Int,
    ): Long? {
        if (durationMs <= 0 || originalBitrateBps <= 0) return null
        val targetVideoBitrateBps = (originalBitrateBps * quality / 100)
            .coerceAtLeast(MIN_VIDEO_BITRATE_BPS)
        val videoBytes = (targetVideoBitrateBps * durationMs) / 8_000L
        val audioAndMuxingBytes = (AUDIO_BITRATE_BPS * durationMs) / 8_000L
        return (videoBytes + audioAndMuxingBytes).coerceIn(0L, originalSize)
    }

    companion object {
        private const val MIN_VIDEO_BITRATE_BPS = 100_000L
        private const val AUDIO_BITRATE_BPS = 128_000L
    }
}

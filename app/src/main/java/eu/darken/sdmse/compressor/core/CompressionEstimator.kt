package eu.darken.sdmse.compressor.core

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
}

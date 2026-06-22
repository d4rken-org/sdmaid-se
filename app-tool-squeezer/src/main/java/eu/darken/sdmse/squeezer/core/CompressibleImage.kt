package eu.darken.sdmse.squeezer.core

import android.graphics.Bitmap
import android.os.Build
import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import kotlinx.parcelize.Parcelize
import java.time.Instant

sealed interface CompressibleMedia {
    val lookup: APathLookup<*>
    val mimeType: String
    val estimatedCompressedSize: Long?
    val wasCompressedBefore: Boolean

    val path: APath
        get() = lookup.lookedUp

    val size: Long
        get() = lookup.size

    val modifiedAt: Instant
        get() = lookup.modifiedAt

    val label: CaString
        get() = lookup.userReadableName

    val identifier: Id
        get() = Id(lookup.path)

    val estimatedSavings: Long?
        get() = estimatedCompressedSize?.let { size - it }?.coerceAtLeast(0)

    @Parcelize
    data class Id(val value: String) : Parcelable
}

data class CompressibleImage(
    override val lookup: APathLookup<*>,
    override val mimeType: String,
    override val estimatedCompressedSize: Long? = null,
    override val wasCompressedBefore: Boolean = false,
) : CompressibleMedia {

    val isJpeg: Boolean
        get() = mimeType == MIME_TYPE_JPEG

    val isWebp: Boolean
        get() = mimeType == MIME_TYPE_WEBP

    val isHeic: Boolean
        get() = mimeType in HEIC_MIME_TYPES

    /** JPEG/WebP only — HEIC has no [Bitmap.CompressFormat]; branch on [isHeic] before reading. */
    val compressFormat: Bitmap.CompressFormat
        get() = Companion.compressFormat(mimeType)

    companion object {
        const val MIME_TYPE_JPEG = "image/jpeg"
        const val MIME_TYPE_WEBP = "image/webp"
        const val MIME_TYPE_HEIC = "image/heic"
        const val MIME_TYPE_HEIF = "image/heif"

        val HEIC_MIME_TYPES = setOf(MIME_TYPE_HEIC, MIME_TYPE_HEIF)

        val SUPPORTED_MIME_TYPES = setOf(MIME_TYPE_JPEG, MIME_TYPE_WEBP) + HEIC_MIME_TYPES

        /**
         * HEIC encoding uses [android.media.HeifWriter], available since API 28 (Android P).
         * Below this, HEIC files must not be surfaced to the user.
         */
        fun isHeicEncodingSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /**
         * The set of mime types this device can actually encode. On pre-P devices HEIC is removed
         * because there is no `HeifWriter`. Used by the scanner and the onboarding sample picker so
         * users never see a HEIC entry they can't process.
         */
        fun deviceSupportedMimeTypes(): Set<String> = if (isHeicEncodingSupported()) {
            SUPPORTED_MIME_TYPES
        } else {
            SUPPORTED_MIME_TYPES - HEIC_MIME_TYPES
        }

        /**
         * Returns the [Bitmap.CompressFormat] for JPEG/WebP. HEIC has no `CompressFormat` entry —
         * callers must check [isHeic] / [HEIC_MIME_TYPES] first and route through `HeifWriter`.
         */
        @Suppress("DEPRECATION")
        fun compressFormat(mimeType: String): Bitmap.CompressFormat {
            require(mimeType !in HEIC_MIME_TYPES) {
                "HEIC has no Bitmap.CompressFormat — route through HeifWriterEncoder instead"
            }
            return when {
                mimeType == MIME_TYPE_WEBP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSY
                mimeType == MIME_TYPE_WEBP -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
        }
    }
}

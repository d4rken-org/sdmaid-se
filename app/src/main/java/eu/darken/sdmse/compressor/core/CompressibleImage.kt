package eu.darken.sdmse.compressor.core

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

    companion object {
        const val MIME_TYPE_JPEG = "image/jpeg"
        const val MIME_TYPE_WEBP = "image/webp"

        val SUPPORTED_MIME_TYPES = setOf(MIME_TYPE_JPEG, MIME_TYPE_WEBP)
    }
}

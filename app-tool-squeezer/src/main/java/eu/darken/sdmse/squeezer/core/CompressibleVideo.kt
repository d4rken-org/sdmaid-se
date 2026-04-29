package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.APathLookup

data class CompressibleVideo(
    override val lookup: APathLookup<*>,
    override val mimeType: String,
    override val estimatedCompressedSize: Long? = null,
    override val wasCompressedBefore: Boolean = false,
    val durationMs: Long,
    val bitrateBps: Long,
) : CompressibleMedia {

    companion object {
        const val MIME_TYPE_MP4 = "video/mp4"

        val SUPPORTED_MIME_TYPES = setOf(MIME_TYPE_MP4)
    }
}

package eu.darken.sdmse.squeezer.ui.preview

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import java.time.Instant

internal fun previewLocalPathLookup(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "DCIM", "Camera", "IMG_20260401.jpg"),
    fileType: FileType = FileType.FILE,
    size: Long = 6L * 1024 * 1024,
    modifiedAt: Instant = Instant.parse("2026-04-01T12:00:00Z"),
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = modifiedAt,
    target = null,
)

internal fun previewCompressibleImage(
    lookup: LocalPathLookup = previewLocalPathLookup(),
    mimeType: String = CompressibleImage.MIME_TYPE_JPEG,
    estimatedCompressedSize: Long? = 4L * 1024 * 1024,
    wasCompressedBefore: Boolean = false,
): CompressibleImage = CompressibleImage(
    lookup = lookup,
    mimeType = mimeType,
    estimatedCompressedSize = estimatedCompressedSize,
    wasCompressedBefore = wasCompressedBefore,
)

internal fun previewCompressibleVideo(
    lookup: LocalPathLookup = previewLocalPathLookup(
        pathSegments = arrayOf("storage", "emulated", "0", "DCIM", "Camera", "VID_20260401.mp4"),
        size = 96L * 1024 * 1024,
    ),
    mimeType: String = CompressibleVideo.MIME_TYPE_MP4,
    estimatedCompressedSize: Long? = 64L * 1024 * 1024,
    wasCompressedBefore: Boolean = false,
    durationMs: Long = 42_000L,
    bitrateBps: Long = 18_000_000L,
): CompressibleVideo = CompressibleVideo(
    lookup = lookup,
    mimeType = mimeType,
    estimatedCompressedSize = estimatedCompressedSize,
    wasCompressedBefore = wasCompressedBefore,
    durationMs = durationMs,
    bitrateBps = bitrateBps,
)

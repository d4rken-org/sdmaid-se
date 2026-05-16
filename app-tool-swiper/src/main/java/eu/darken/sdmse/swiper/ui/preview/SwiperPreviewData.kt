package eu.darken.sdmse.swiper.ui.preview

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import java.time.Instant

internal fun previewLocalPathLookup(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "DCIM", "old_photo.jpg"),
    fileType: FileType = FileType.FILE,
    size: Long = 2L * 1024 * 1024,
    modifiedAt: Instant = Instant.parse("2025-01-15T08:00:00Z"),
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = modifiedAt,
    target = null,
)

internal fun previewSwipeItem(
    id: Long = 1L,
    sessionId: String = "session-1",
    itemIndex: Int = 0,
    lookup: LocalPathLookup = previewLocalPathLookup(),
    decision: SwipeDecision = SwipeDecision.UNDECIDED,
): SwipeItem = SwipeItem(
    id = id,
    sessionId = sessionId,
    itemIndex = itemIndex,
    lookup = lookup,
    decision = decision,
)

package eu.darken.sdmse.systemcleaner.ui.preview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.filterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.stock.EmptyDirectoryFilter
import eu.darken.sdmse.systemcleaner.ui.list.SystemCleanerListViewModel
import java.time.Instant

private fun previewLookup(
    pathSegments: Array<String>,
    fileType: FileType = FileType.FILE,
    size: Long = 1024L * 1024,
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
    target = null,
)

internal fun previewFilterContent(
    identifier: String = EmptyDirectoryFilter::class.filterIdentifier,
    icon: ImageVector = Icons.TwoTone.Delete,
    label: String = "Empty directories",
    description: String = "Folders that contain no files.",
    items: Collection<SystemCleanerFilter.Match> = listOf(
        SystemCleanerFilter.Match.Deletion(
            lookup = previewLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "stale.cache"),
                size = 4L * 1024 * 1024,
            ),
        ),
        SystemCleanerFilter.Match.Deletion(
            lookup = previewLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "old.tmp"),
                size = 2L * 1024 * 1024,
            ),
        ),
    ),
): FilterContent = FilterContent(
    identifier = identifier,
    icon = icon,
    label = label.toCaString(),
    description = description.toCaString(),
    items = items,
)

internal fun previewSystemCleanerRow(
    content: FilterContent = previewFilterContent(),
): SystemCleanerListViewModel.Row = SystemCleanerListViewModel.Row(content = content)

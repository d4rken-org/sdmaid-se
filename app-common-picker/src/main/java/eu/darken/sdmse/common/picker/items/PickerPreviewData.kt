package eu.darken.sdmse.common.picker.items

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.picker.PickerItem
import eu.darken.sdmse.common.picker.PickerViewModel
import eu.darken.sdmse.common.user.UserHandle2
import java.time.Instant

internal fun previewLocalPathLookup(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "Download"),
    fileType: FileType = FileType.DIRECTORY,
    size: Long = 4L * 1024 * 1024,
    modifiedAt: Instant = Instant.parse("2026-04-01T12:00:00Z"),
): LocalPathLookup = LocalPathLookup(
    lookedUp = LocalPath.build(*pathSegments),
    fileType = fileType,
    size = size,
    modifiedAt = modifiedAt,
    target = null,
)

internal fun previewDataArea(
    path: LocalPath = LocalPath.build("storage", "emulated", "0"),
    type: DataArea.Type = DataArea.Type.SDCARD,
    label: String = "Primary storage",
): DataArea = DataArea(
    path = path,
    type = type,
    label = label.toCaString(),
    userHandle = UserHandle2(handleId = 0),
)

internal fun previewPickerItem(
    lookup: LocalPathLookup = previewLocalPathLookup(),
    parent: PickerItem? = null,
    dataArea: DataArea = previewDataArea(),
    selected: Boolean = false,
    selectable: Boolean = true,
): PickerItem = PickerItem(
    lookup = lookup,
    parent = parent,
    dataArea = dataArea,
    selected = selected,
    selectable = selectable,
)

internal fun previewPickerRootRow(): PickerViewModel.PickerRow = PickerViewModel.PickerRow(
    item = previewPickerItem(
        lookup = previewLocalPathLookup(pathSegments = arrayOf("storage", "emulated", "0")),
        parent = null,
        selected = false,
    ),
)

internal fun previewPickerChildRow(selected: Boolean = false): PickerViewModel.PickerRow {
    val root = previewPickerItem(
        lookup = previewLocalPathLookup(pathSegments = arrayOf("storage", "emulated", "0")),
        parent = null,
    )
    return PickerViewModel.PickerRow(
        item = previewPickerItem(
            lookup = previewLocalPathLookup(pathSegments = arrayOf("storage", "emulated", "0", "Download")),
            parent = root,
            selected = selected,
        ),
    )
}

internal fun previewSelectedRow(
    pathSegments: Array<String> = arrayOf("storage", "emulated", "0", "Download"),
): PickerViewModel.SelectedRow = PickerViewModel.SelectedRow(
    lookup = previewLocalPathLookup(pathSegments = pathSegments),
)

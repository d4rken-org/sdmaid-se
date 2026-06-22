package eu.darken.sdmse.common.picker

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.picker.items.previewDataArea
import eu.darken.sdmse.common.picker.items.previewLocalPathLookup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PickerRowTest : BaseTest() {

    private val area = previewDataArea()

    private fun item(fileType: FileType, isRoot: Boolean): PickerItem = PickerItem(
        lookup = previewLocalPathLookup(fileType = fileType),
        parent = if (isRoot) null else item(FileType.DIRECTORY, isRoot = true),
        dataArea = area,
        selected = false,
        selectable = true,
    )

    private fun row(fileType: FileType, isRoot: Boolean): PickerViewModel.PickerRow =
        PickerViewModel.PickerRow(item(fileType, isRoot))

    @Test
    fun `area roots are navigable`() {
        row(FileType.DIRECTORY, isRoot = true).navigable shouldBe true
    }

    @Test
    fun `directories are navigable`() {
        row(FileType.DIRECTORY, isRoot = false).navigable shouldBe true
    }

    @Test
    fun `files are not navigable - body tap selects instead of opening`() {
        row(FileType.FILE, isRoot = false).navigable shouldBe false
    }

    @Test
    fun `symlinks are not navigable`() {
        row(FileType.SYMBOLIC_LINK, isRoot = false).navigable shouldBe false
    }
}

package eu.darken.sdmse.analyzer.ui.storage.content

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.analyzer.ui.storage.preview.previewContentItem
import eu.darken.sdmse.analyzer.ui.storage.preview.previewDeviceStorage
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.ui.LayoutMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class ContentScreenTest : BaseComposeRobolectricTest() {

    private val itemPath = LocalPath.build("storage", "emulated", "0", "DCIM")

    private val item = ContentViewModel.Item(
        parent = null,
        content = previewContentItem(
            segments = arrayOf("storage", "emulated", "0", "DCIM"),
            type = FileType.DIRECTORY,
        ),
        sizeRatio = 1f,
    )

    private fun readyState(externalFolder: APath?) = ContentViewModel.State.Ready(
        title = "Media".toCaString(),
        subtitle = "DCIM".toCaString(),
        storage = previewDeviceStorage(),
        items = listOf(item),
        layoutMode = LayoutMode.LINEAR,
        progress = null,
        isReadOnly = false,
        infoBanner = null,
        externalFolder = externalFolder,
    )

    private fun ComposeContentTestRule.setContentScreen(
        externalFolder: APath?,
        onOpenExternally: (APath) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                ContentScreen(
                    stateSource = MutableStateFlow(readyState(externalFolder)),
                    onOpenExternally = onOpenExternally,
                )
            }
        }
    }

    @Test
    fun `without an external folder the action is hidden`() {
        composeRule.setContentScreen(externalFolder = null)

        composeRule.onNodeWithContentDescription("Open in file manager").assertDoesNotExist()
    }

    @Test
    fun `with an external folder the action is shown`() {
        composeRule.setContentScreen(externalFolder = itemPath)

        composeRule.onNodeWithContentDescription("Open in file manager").assertExists()
    }

    @Test
    fun `tapping the action reports the external folder`() {
        val opened = mutableListOf<APath>()
        composeRule.setContentScreen(externalFolder = itemPath, onOpenExternally = { opened.add(it) })

        composeRule.onNodeWithContentDescription("Open in file manager").performClick()

        opened shouldBe listOf(itemPath)
    }

    @Test
    fun `the action is hidden while the selection top bar is showing`() {
        composeRule.setContentScreen(externalFolder = itemPath)

        // Without a parent level the row renders the item's default label: its full path.
        composeRule.onNodeWithText("/storage/emulated/0/DCIM").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 item").assertExists()
        composeRule.onNodeWithContentDescription("Open in file manager").assertDoesNotExist()
    }
}

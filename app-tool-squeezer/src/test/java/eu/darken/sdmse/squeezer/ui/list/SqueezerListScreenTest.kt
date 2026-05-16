package eu.darken.sdmse.squeezer.ui.list

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant

class SqueezerListScreenTest : BaseComposeRobolectricTest() {

    private fun image(name: String, size: Long = 1024L): CompressibleImage = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("/storage/$name")),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
    )

    private fun ComposeContentTestRule.setListScreen(state: SqueezerListViewModel.State) {
        setContent {
            PreviewWrapper {
                SqueezerListScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    @Test
    fun `loading state - media null - shows the tool name and no Empty marker`() {
        composeRule.setListScreen(SqueezerListViewModel.State(media = null))

        // SdmEmptyState text "Empty" must not appear during loading. Squeezer's strings put the
        // tool name as "Media Squeeze".
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        composeRule.onNodeWithText("Media Squeeze").assertExists()
    }

    @Test
    fun `empty state - media empty - shows the Empty placeholder`() {
        composeRule.setListScreen(SqueezerListViewModel.State(media = emptyList()))

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state - renders each row by its file name`() {
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(image("alpha.jpg"), image("beta.jpg"), image("gamma.jpg")),
            ),
        )

        composeRule.onNodeWithText("alpha.jpg").assertExists()
        composeRule.onNodeWithText("beta.jpg").assertExists()
        composeRule.onNodeWithText("gamma.jpg").assertExists()
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `tapping a row with no selection routes to onCompressIds with single id`() {
        // The Linear row's tap callback short-circuits to `onCompressIds(setOf(item.identifier))`
        // when `selection.isEmpty()`. Use a single-item list and the linear (default) layout —
        // tapping the row text should fire compress, NOT the preview.
        val a = image("only.jpg")
        var compressed: Set<CompressibleMedia.Id>? = null
        composeRule.setContent {
            PreviewWrapper {
                SqueezerListScreen(
                    stateSource = MutableStateFlow(SqueezerListViewModel.State(media = listOf(a))),
                    onCompressIds = { compressed = it },
                )
            }
        }

        composeRule.onNodeWithText("only.jpg").performClick()

        compressed shouldBe setOf(a.identifier)
    }

    @Test
    fun `Compress all FAB visible only when media is non-empty and no selection`() {
        // The ExtendedFAB renders the localized "Compress all" label. When media is empty the
        // FAB is not rendered — catches a regression that always renders the FAB.
        composeRule.setListScreen(SqueezerListViewModel.State(media = emptyList()))

        composeRule.onAllNodesWithText("Compress all").assertCountEquals(0)
    }

    @Test
    fun `Compress all FAB visible when media is non-empty`() {
        // ExtendedFloatingActionButton merges its text + icon into a Button role, so the inner
        // Text node only shows up in the unmerged semantics tree. Without useUnmergedTree=true
        // the finder traverses only the merged tree and misses the text content.
        composeRule.setListScreen(
            SqueezerListViewModel.State(media = listOf(image("a.jpg"))),
        )

        composeRule.onNodeWithText("Compress all", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Compress all FAB triggers onCompressAll callback`() {
        var clicked = 0
        composeRule.setContent {
            PreviewWrapper {
                SqueezerListScreen(
                    stateSource = MutableStateFlow(
                        SqueezerListViewModel.State(media = listOf(image("a.jpg"))),
                    ),
                    onCompressAll = { clicked++ },
                )
            }
        }

        // The FAB merges its text into the button role; click the button itself (which IS in
        // the merged tree) via the unmerged-tree text node's parent. Simplest path: tap the
        // text and let the click bubble to the FAB's onClick.
        composeRule.onNodeWithText("Compress all", useUnmergedTree = true).performClick()

        clicked shouldBe 1
    }

    @Test
    fun `layout toggle icon present when media is non-empty - GRID mode shows list icon`() {
        // The IconButton is only rendered when `media.isNotEmpty()`. Pin the content description
        // so a regression that always or never renders it surfaces here. The contentDescription
        // resolves from CommonR.string.general_toggle_layout_mode.
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(image("a.jpg")),
                layoutMode = eu.darken.sdmse.common.ui.LayoutMode.GRID,
            ),
        )

        composeRule.onNodeWithContentDescription("Switch view mode").assertExists()
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}

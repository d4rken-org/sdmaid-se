package eu.darken.sdmse.squeezer.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import io.mockk.mockk
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.PriorCompression
import eu.darken.sdmse.squeezer.ui.list.items.SqueezerListGridCardTags
import eu.darken.sdmse.squeezer.ui.list.items.SqueezerListLinearRowTags
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant

class SqueezerListScreenTest : BaseComposeRobolectricTest() {

    // The list screen reads LocalGuidedTourController; supply a relaxed mock (shouldStart() defaults
    // to false, so no tour starts) — these tests stay focused on the list UI.
    private val mockTourController: GuidedTourController = mockk(relaxed = true)

    private fun image(
        name: String,
        size: Long = 1024L,
        priorCompression: PriorCompression? = null,
        hasLossyAux: Boolean = false,
        hasMotionVideo: Boolean = false,
        willDownscale: Boolean = false,
    ): CompressibleImage = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("/storage/$name")),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
        priorCompression = priorCompression,
        hasLossyAux = hasLossyAux,
        hasMotionVideo = hasMotionVideo,
        willDownscale = willDownscale,
    )

    private fun ComposeContentTestRule.setListScreen(state: SqueezerListViewModel.State) {
        setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    SqueezerListScreen(stateSource = MutableStateFlow(state))
                }
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
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    SqueezerListScreen(
                        stateSource = MutableStateFlow(SqueezerListViewModel.State(media = listOf(a))),
                        onCompressIds = { compressed = it },
                    )
                }
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
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    SqueezerListScreen(
                        stateSource = MutableStateFlow(
                            SqueezerListViewModel.State(media = listOf(image("a.jpg"))),
                        ),
                        onCompressAll = { clicked++ },
                    )
                }
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

    @Test
    fun `linear row - compressed-before marker renders its chip`() {
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(image("a.jpg", priorCompression = PriorCompression.COMPRESSED)),
            ),
        )

        composeRule.onNodeWithText("Compressed").assertExists()
        composeRule.onAllNodesWithText("HDR/depth").assertCountEquals(0)
    }

    @Test
    fun `linear row - a no-savings item renders no chip`() {
        // NO_SAVINGS still gates skip-previously-compressed, it just isn't worth a marker.
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(image("a.jpg", priorCompression = PriorCompression.NO_SAVINGS)),
            ),
        )

        composeRule.onAllNodesWithText("Compressed").assertCountEquals(0)
        composeRule.onAllNodesWithText("HDR/depth").assertCountEquals(0)
        // A text-absence assertion can't see a container that holds no text, so on its own it
        // also passes if a marker renders under a different label, or if the early-return guard
        // regresses and an empty container is composed. The clickable row merges child semantics
        // and drops the tag, so the tag lookup needs the unmerged tree.
        composeRule
            .onAllNodesWithTag(SqueezerListLinearRowTags.MARKER_ROW, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `linear row - HDR-depth marker renders its chip`() {
        composeRule.setListScreen(
            SqueezerListViewModel.State(media = listOf(image("a.jpg", hasLossyAux = true))),
        )

        composeRule.onNodeWithText("HDR/depth").assertExists()
    }

    @Test
    fun `linear row - motion photo and downscale markers render their chips`() {
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(image("a.jpg", hasMotionVideo = true, willDownscale = true)),
            ),
        )

        composeRule.onNodeWithText("Motion Photo").assertExists()
        composeRule.onNodeWithText("Downscaled").assertExists()
        composeRule.onAllNodesWithText("Compressed").assertCountEquals(0)
        composeRule.onAllNodesWithText("HDR/depth").assertCountEquals(0)
    }

    @Test
    fun `linear row - an unmarked item renders no chips`() {
        composeRule.setListScreen(
            SqueezerListViewModel.State(media = listOf(image("a.jpg"))),
        )

        composeRule.onAllNodesWithText("Compressed").assertCountEquals(0)
        composeRule.onAllNodesWithText("HDR/depth").assertCountEquals(0)
    }

    @Test
    fun `grid card - each marker renders its own labelled chip`() {
        // The grid lays the linear row's chips over the preview, so each marker is identified by
        // its label rather than by a glyph. The middle item carries the no-savings state and must
        // contribute nothing, so both counts stay at one and only two of the three cards compose
        // a marker container.
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(
                    image("a.jpg", priorCompression = PriorCompression.COMPRESSED),
                    image("b.jpg", priorCompression = PriorCompression.NO_SAVINGS),
                    image("c.jpg", hasLossyAux = true),
                ),
                layoutMode = eu.darken.sdmse.common.ui.LayoutMode.GRID,
            ),
        )

        composeRule.onAllNodesWithText("Compressed").assertCountEquals(1)
        composeRule.onAllNodesWithText("HDR/depth").assertCountEquals(1)
        composeRule
            .onAllNodesWithTag(SqueezerListGridCardTags.MARKER_ROW, useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test
    fun `grid card - a no-savings item renders no marker chip`() {
        composeRule.setListScreen(
            SqueezerListViewModel.State(
                media = listOf(image("b.jpg", priorCompression = PriorCompression.NO_SAVINGS)),
                layoutMode = eu.darken.sdmse.common.ui.LayoutMode.GRID,
            ),
        )

        composeRule.onAllNodesWithText("Compressed").assertCountEquals(0)
        composeRule.onAllNodesWithText("HDR/depth").assertCountEquals(0)
        composeRule
            .onAllNodesWithTag(SqueezerListGridCardTags.MARKER_ROW, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `markers dialog - explains every marker`() {
        composeRule.setContent {
            PreviewWrapper {
                SqueezerMarkersDialog(onDismiss = {})
            }
        }

        composeRule.onAllNodesWithText("Compressed").fetchSemanticsNodes().isNotEmpty() shouldBe true
        composeRule.onAllNodesWithText("HDR/depth").fetchSemanticsNodes().isNotEmpty() shouldBe true
        composeRule.onAllNodesWithText("Motion Photo").fetchSemanticsNodes().isNotEmpty() shouldBe true
        composeRule.onAllNodesWithText("Downscaled").fetchSemanticsNodes().isNotEmpty() shouldBe true

        composeRule.onNodeWithText(
            "SD Maid already compressed this file. Compressing it again costs quality and saves little.",
        ).assertExists()
        composeRule.onNodeWithText(
            "This photo has HDR or depth data that compression can't keep. An HDR photo comes back as a " +
                "normal one, and a portrait photo loses the depth data used for background blur.",
        ).assertExists()
        composeRule.onNodeWithText(
            "This is a Motion Photo. Compression keeps the still image and removes the video clip.",
        ).assertExists()
        composeRule.onNodeWithText(
            "This photo is wider or taller than 8192 pixels. Compression also halves its resolution.",
        ).assertExists()
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}

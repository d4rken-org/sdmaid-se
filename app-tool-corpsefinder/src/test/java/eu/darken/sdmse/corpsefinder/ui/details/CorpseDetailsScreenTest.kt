package eu.darken.sdmse.corpsefinder.ui.details

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest

// HorizontalPager + ScrollableTabRow are known to interact poorly with Robolectric for
// multi-page assertions. Tests here intentionally stay on single-item / empty state and avoid
// pager swipe interactions or tab-switching.
class CorpseDetailsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setDetailsScreen(state: CorpseDetailsViewModel.State) {
        setContent {
            PreviewWrapper {
                CorpseDetailsScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    // The screen stacks three scrollables (tab strip, pager, page list); only the page list scrolls
    // vertically, so match on that axis to stay unambiguous.
    private fun ComposeContentTestRule.scrollTo(text: String) {
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(text))
    }

    // Same axis filter, but every rendered pager page contributes one vertical scrollable, so the
    // page has to be addressed by index once more than one is on screen.
    private fun ComposeContentTestRule.scrollPageTo(pageIndex: Int, text: String) {
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))[pageIndex]
            .performScrollToNode(hasText(text))
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setDetailsScreen(CorpseDetailsViewModel.State(items = emptyList()))

        // The "Details" subtitle in the top bar appears unconditionally (it's part of the
        // non-selection TopAppBar), so we don't assert against it. The empty placeholder text
        // is the meaningful signal of the empty branch.
        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state renders the corpse content body — not just the top bar`() {
        val onlyCorpse = previewCorpse(
            lookup = previewLocalPathLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "single.dat"),
                size = 100L,
            ),
            content = emptyList(),
        )
        composeRule.setDetailsScreen(
            CorpseDetailsViewModel.State(
                items = listOf(onlyCorpse),
                target = onlyCorpse.identifier,
            ),
        )

        composeRule.onNodeWithText("CorpseFinder").assertExists()
        // Empty placeholder must NOT render when items are present.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // Assert the CorpseContent body is actually composed — not just the top bar. Two
        // labels are unique to that content (avoids confusion with the top-bar "Details"
        // subtitle, which renders in both empty and populated states).
        composeRule.onNodeWithText("Path").assertExists()
        composeRule.onNodeWithText("Owners").assertExists()
    }

    @Test
    fun `populated state renders the corpse tab label`() {
        // For single-item state, both the tab row and the pager surface the corpse's `name`.
        // We rely on the lookup's `name` property (last path segment) to produce the tab text.
        val onlyCorpse = previewCorpse(
            lookup = previewLocalPathLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "tab-target.dat"),
                size = 100L,
            ),
            content = emptyList(),
        )
        composeRule.setDetailsScreen(
            CorpseDetailsViewModel.State(
                items = listOf(onlyCorpse),
                target = onlyCorpse.identifier,
            ),
        )

        composeRule.onNodeWithText("tab-target.dat").assertExists()
    }

    @Test
    fun `long-pressing a file row gates the header card actions`() {
        // Drives the screen's own `!selection.isActive` computation instead of injecting the flag.
        val corpseRoot = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "gate.target"),
        )
        val onlyCorpse = previewCorpse(
            lookup = corpseRoot,
            content = listOf(
                previewLocalPathLookup(
                    pathSegments = arrayOf(
                        "storage", "emulated", "0", "Android", "data", "gate.target", "gated.bin",
                    ),
                    fileType = FileType.FILE,
                    size = 8L * 1024 * 1024,
                ),
            ),
        )
        composeRule.setDetailsScreen(
            CorpseDetailsViewModel.State(
                items = listOf(onlyCorpse),
                target = onlyCorpse.identifier,
            ),
        )

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()

        composeRule.scrollTo("gated.bin")
        composeRule.onNodeWithText("gated.bin").performTouchInput { longClick() }

        // The header card fills the viewport, so the file row is only reachable after a scroll.
        composeRule.scrollTo("Exclude")
        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w720dp-h1024dp")
    fun `on a wide viewport the gate covers the visible neighbour page too`() {
        // spanCount is (screenWidthDp / 390 + 0.5).toInt(), so 720dp yields two pages side by side
        // and the neighbour's header card is on screen while the first page owns the selection.
        val focused = previewCorpse(
            lookup = previewLocalPathLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "focused.target"),
            ),
            content = listOf(
                previewLocalPathLookup(
                    pathSegments = arrayOf(
                        "storage", "emulated", "0", "Android", "data", "focused.target", "focused.bin",
                    ),
                    fileType = FileType.FILE,
                    size = 8L * 1024 * 1024,
                ),
            ),
        )
        val neighbour = previewCorpse(
            lookup = previewLocalPathLookup(
                pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", "neighbour.target"),
            ),
            content = listOf(
                previewLocalPathLookup(
                    pathSegments = arrayOf(
                        "storage", "emulated", "0", "Android", "data", "neighbour.target", "neighbour.bin",
                    ),
                    fileType = FileType.FILE,
                    size = 4L * 1024 * 1024,
                ),
            ),
        )
        composeRule.setDetailsScreen(
            CorpseDetailsViewModel.State(
                items = listOf(focused, neighbour),
                target = focused.identifier,
            ),
        )

        composeRule.onAllNodesWithText("Exclude").assertCountEquals(2)
        composeRule.onAllNodesWithText("Delete").assertCountEquals(2)

        composeRule.scrollPageTo(0, "focused.bin")
        composeRule.onNodeWithText("focused.bin").performTouchInput { longClick() }
        composeRule.scrollPageTo(0, "Exclude")

        // Both header cards must gate, not just the selection owner's: a page-scoped gate would
        // leave the neighbour's whole-corpse Delete/Exclude live.
        composeRule.onAllNodesWithText("Exclude").assertCountEquals(2)
        composeRule.onAllNodesWithText("Delete").assertCountEquals(2)
        repeat(2) { index ->
            composeRule.onAllNodesWithText("Exclude")[index].assertIsNotEnabled()
            composeRule.onAllNodesWithText("Delete")[index].assertIsNotEnabled()
        }
    }
}

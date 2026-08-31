package eu.darken.sdmse.systemcleaner.ui.details

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterItems
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest

// HorizontalPager + ScrollableTabRow are known to interact poorly with Robolectric for
// multi-page assertions. Tests stay on single-item / empty states.
class FilterContentDetailsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setDetailsScreen(state: FilterContentDetailsViewModel.State) {
        setContent {
            PreviewWrapper {
                FilterContentDetailsScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setDetailsScreen(FilterContentDetailsViewModel.State(items = emptyList()))

        // SystemCleaner top bar title is always visible.
        composeRule.onNodeWithText("SystemCleaner").assertExists()
        // Empty placeholder for drained state.
        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state renders the filter content body and tab label`() {
        val fc = previewFilterContent(
            identifier = "fc-target",
            label = "Empty directories",
            description = "Folders that contain no files.",
        )
        composeRule.setDetailsScreen(
            FilterContentDetailsViewModel.State(
                items = listOf(fc),
                target = fc.identifier,
            ),
        )

        composeRule.onNodeWithText("SystemCleaner").assertExists()
        // Empty placeholder must NOT render when items are present.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // The filter label appears in the tab row.
        composeRule.onAllNodesWithText("Empty directories").fetchSemanticsNodes().size.let {
            if (it == 0) throw AssertionError("Expected the filter label visible in tab/title")
        }
    }

    @Test
    fun `populated state renders the filter description in the header card`() {
        // Filter description text comes from FilterContent.description and renders in the
        // header card above the file list.
        val fc = previewFilterContent(
            identifier = "fc-with-desc",
            label = "Sample",
            description = "Custom description text in header.",
        )
        composeRule.setDetailsScreen(
            FilterContentDetailsViewModel.State(
                items = listOf(fc),
                target = fc.identifier,
            ),
        )

        composeRule.onNodeWithText("Custom description text in header.").assertExists()
    }

    @Test
    fun `long-pressing a file row gates the header card actions`() {
        // Drives the screen's own `!selection.isActive` computation instead of injecting the flag.
        val fc = previewFilterContent(identifier = "fc-gate", label = "Gate me")
        composeRule.setDetailsScreen(
            FilterContentDetailsViewModel.State(
                items = listOf(fc),
                target = fc.identifier,
            ),
        )

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()

        composeRule.onNodeWithText(fc.items.first().path.path).performTouchInput { longClick() }

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w720dp-h1024dp")
    fun `on a wide viewport the gate covers the visible neighbour page too`() {
        // spanCount is (screenWidthDp / 390 + 0.5).toInt(), so 720dp yields two pages side by side
        // and the neighbour's header card is on screen while the first page owns the selection.
        val focused = previewFilterContent(identifier = "fc-focused", label = "Focused")
        val neighbour = previewFilterContent(
            identifier = "fc-neighbour",
            label = "Neighbour",
            items = previewFilterItems(itemCount = 2, totalSize = 4L * 1024 * 1024),
        )
        composeRule.setDetailsScreen(
            FilterContentDetailsViewModel.State(
                items = listOf(focused, neighbour),
                target = focused.identifier,
            ),
        )

        composeRule.onAllNodesWithText("Exclude").assertCountEquals(2)
        composeRule.onAllNodesWithText("Delete").assertCountEquals(2)

        composeRule.onNodeWithText(focused.items.first().path.path).performTouchInput { longClick() }

        // Both header cards must gate, not just the selection owner's: a page-scoped gate would
        // leave the neighbour's whole-filter Delete/Exclude live.
        composeRule.onAllNodesWithText("Exclude").assertCountEquals(2)
        composeRule.onAllNodesWithText("Delete").assertCountEquals(2)
        repeat(2) { index ->
            composeRule.onAllNodesWithText("Exclude")[index].assertIsNotEnabled()
            composeRule.onAllNodesWithText("Delete")[index].assertIsNotEnabled()
        }
    }
}

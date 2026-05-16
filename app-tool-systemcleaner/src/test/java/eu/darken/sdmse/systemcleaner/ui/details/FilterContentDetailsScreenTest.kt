package eu.darken.sdmse.systemcleaner.ui.details

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
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
}

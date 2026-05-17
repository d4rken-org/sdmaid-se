package eu.darken.sdmse.systemcleaner.ui.list

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import eu.darken.sdmse.systemcleaner.ui.preview.previewSystemCleanerRow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SystemCleanerListScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setListScreen(state: SystemCleanerListViewModel.State) {
        setContent {
            PreviewWrapper {
                SystemCleanerListScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    @Test
    fun `loading state shows no rows or empty marker`() {
        composeRule.setListScreen(SystemCleanerListViewModel.State(rows = null))

        // "Empty" placeholder must not appear during loading.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // Tool name from the top bar is always visible.
        composeRule.onNodeWithText("SystemCleaner").assertExists()
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setListScreen(SystemCleanerListViewModel.State(rows = emptyList()))

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state renders rows by filter label`() {
        composeRule.setListScreen(
            SystemCleanerListViewModel.State(
                rows = listOf(
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "alpha", label = "Alpha filter")),
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "beta", label = "Beta filter")),
                ),
            ),
        )

        composeRule.onNodeWithText("Alpha filter").assertExists()
        composeRule.onNodeWithText("Beta filter").assertExists()
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `long-press on row enters selection mode and shows selection top bar`() {
        composeRule.setListScreen(
            SystemCleanerListViewModel.State(
                rows = listOf(
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "a", label = "A row")),
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "b", label = "B row")),
                ),
            ),
        )

        // Before long-press: tool-name top bar visible, no selection top bar.
        composeRule.onNodeWithText("SystemCleaner").assertExists()

        composeRule.onNodeWithText("A row").performTouchInput { longClick() }

        // After long-press the selection top bar appears. Its actions use stable content
        // descriptions — "Delete" is the most identifying.
        composeRule.onAllNodesWithContentDescription("Delete selected").fetchSemanticsNodes().size.let {
            if (it == 0) throw AssertionError("Expected Delete action visible after long-press")
        }
    }

    @Test
    fun `selection clear action exits selection mode`() {
        composeRule.setListScreen(
            SystemCleanerListViewModel.State(
                rows = listOf(
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "a", label = "A row")),
                ),
            ),
        )
        composeRule.onNodeWithText("A row").performTouchInput { longClick() }
        // Clear-action button is exposed with the "Close" content description from
        // SdmSelectionTopAppBar.kt:58 (general_close_action).
        composeRule.onNodeWithContentDescription("Close").performClick()

        // After clearing, the regular top bar with tool name should be back.
        composeRule.onNodeWithText("SystemCleaner").assertExists()
        // "Delete selected" action should no longer be visible.
        composeRule.onAllNodesWithContentDescription("Delete selected").assertCountEquals(0)
    }

    @Test
    fun `select-all action visible only with partial selection`() {
        composeRule.setListScreen(
            SystemCleanerListViewModel.State(
                rows = listOf(
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "a", label = "A row")),
                    previewSystemCleanerRow(content = previewFilterContent(identifier = "b", label = "B row")),
                ),
            ),
        )
        // Partial selection: select only A.
        composeRule.onNodeWithText("A row").performTouchInput { longClick() }

        composeRule.onAllNodesWithContentDescription("Select all").fetchSemanticsNodes().size.let {
            if (it == 0) throw AssertionError("Expected Select all action visible with partial selection")
        }
    }
}

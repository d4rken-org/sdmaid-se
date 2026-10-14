package eu.darken.sdmse.appcleaner.ui.list

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.appcleaner.ui.preview.previewAppCleanerRow
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AppCleanerListScreenTest : BaseComposeRobolectricTest() {

    private fun row(pkgName: String, label: String = pkgName): AppCleanerListViewModel.Row =
        previewAppCleanerRow(
            junk = previewAppJunk(pkg = previewInstalled(pkgName = pkgName, label = label)),
        )

    private fun ComposeContentTestRule.setListScreen(
        state: AppCleanerListViewModel.State,
        onHelp: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                AppCleanerListScreen(stateSource = MutableStateFlow(state), onHelp = onHelp)
            }
        }
    }

    @Test
    fun `loading state does not show the empty placeholder`() {
        composeRule.setListScreen(AppCleanerListViewModel.State(rows = null))

        // "Empty" must NOT render while rows is null (loading).
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // Tool name from the top bar is always visible.
        composeRule.onNodeWithText("AppCleaner").assertExists()
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setListScreen(AppCleanerListViewModel.State(rows = emptyList()))

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `no-matches state shows the search-no-matches placeholder`() {
        composeRule.setListScreen(
            AppCleanerListViewModel.State(
                rows = emptyList(),
                searchQuery = "xyz",
                isSearchFilterActive = true,
                totalCount = 5,
            ),
        )

        // Resolves to CommonR.string.general_search_no_matches → "No matches"
        composeRule.onNodeWithText("No matches").assertExists()
    }

    @Test
    fun `populated state renders each row by its app label`() {
        composeRule.setListScreen(
            AppCleanerListViewModel.State(
                rows = listOf(
                    row(pkgName = "com.example.alpha", label = "Alpha"),
                    row(pkgName = "com.example.beta", label = "Beta"),
                ),
            ),
        )

        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
        // The empty placeholder must not render when rows are present.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `help action shows in the normal top bar and invokes onHelp`() {
        var help = 0
        composeRule.setListScreen(
            AppCleanerListViewModel.State(rows = listOf(row(pkgName = "com.example.alpha", label = "Alpha"))),
            onHelp = { help++ },
        )

        composeRule.onNodeWithContentDescription("Help").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, help) }
    }

    @Test
    fun `help action is hidden while search is open`() {
        composeRule.setListScreen(AppCleanerListViewModel.State(rows = emptyList()))
        composeRule.onNodeWithContentDescription("Help").assertExists()

        composeRule.onNodeWithContentDescription("Search").performClick()

        composeRule.onNodeWithText("AppCleaner").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Help").assertDoesNotExist()
    }

    @Test
    fun `help action is hidden in selection mode`() {
        composeRule.setListScreen(
            AppCleanerListViewModel.State(rows = listOf(row(pkgName = "com.example.alpha", label = "Alpha"))),
        )
        composeRule.onNodeWithContentDescription("Help").assertExists()

        composeRule.onNodeWithText("Alpha").performTouchInput { longClick() }

        composeRule.onNodeWithContentDescription("Delete selected").assertExists()
        composeRule.onNodeWithContentDescription("Help").assertDoesNotExist()
    }
}

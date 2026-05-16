package eu.darken.sdmse.corpsefinder.ui.details

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
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
}

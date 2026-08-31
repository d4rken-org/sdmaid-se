package eu.darken.sdmse.appcleaner.ui.details

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
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

// HorizontalPager + ScrollableTabRow are known to interact poorly with Robolectric for
// multi-page assertions. Tests here intentionally stay on single-item / empty state and avoid
// pager swipe interactions or tab-switching.
class AppJunkDetailsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setDetailsScreen(state: AppJunkDetailsViewModel.State) {
        setContent {
            PreviewWrapper {
                AppJunkDetailsScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    // The screen stacks three scrollables (tab strip, pager, page list); only the page list scrolls
    // vertically, so match on that axis to stay unambiguous.
    private fun ComposeContentTestRule.scrollTo(text: String) {
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(text))
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setDetailsScreen(AppJunkDetailsViewModel.State(items = emptyList()))

        // The "Details" subtitle in the top bar appears unconditionally, so we don't assert
        // against it. The empty placeholder text is the meaningful signal of the empty branch.
        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated state renders the AppCleaner top bar and not the Empty placeholder`() {
        val a = previewAppJunk(pkg = previewInstalled(pkgName = "com.example.a", label = "Alpha"))
        composeRule.setDetailsScreen(
            AppJunkDetailsViewModel.State(
                items = listOf(a),
                target = a.identifier,
            ),
        )

        composeRule.onNodeWithText("AppCleaner").assertExists()
        // Empty placeholder must NOT render when items are present.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `long-pressing a file row gates the header card actions`() {
        // Drives the screen's own `!selection.isActive` computation instead of injecting the flag.
        val a = previewAppJunk(pkg = previewInstalled(pkgName = "com.example.gate", label = "Gate"))
        composeRule.setDetailsScreen(
            AppJunkDetailsViewModel.State(
                items = listOf(a),
                target = a.identifier,
            ),
        )

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()

        val filePath = a.expendables!!.values.first().first().path.path
        composeRule.scrollTo(filePath)
        composeRule.onNodeWithText(filePath).performTouchInput { longClick() }

        // The header card scrolls out of view on the way down to the file rows.
        composeRule.scrollTo("Exclude")
        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    // NOTE: Asserting on tab/pager-only content (e.g. junk.label rendered as the tab title) is
    // brittle under Robolectric + HorizontalPager + ScrollableTabRow — the tab row's measurement
    // pass doesn't always lay out its children in the headless Compose harness. CorpseDetails's
    // test gets away with asserting on path-segment text because that string is also rendered in
    // the body (Path / Owners labels). AppJunkPage doesn't surface the app label in the body, so
    // there's no second render to anchor against. Multi-junk pager interactions belong in
    // instrumentation tests if they're worth covering.
}

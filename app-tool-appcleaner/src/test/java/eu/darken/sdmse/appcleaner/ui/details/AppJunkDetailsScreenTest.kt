package eu.darken.sdmse.appcleaner.ui.details

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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

    // NOTE: Asserting on tab/pager-only content (e.g. junk.label rendered as the tab title) is
    // brittle under Robolectric + HorizontalPager + ScrollableTabRow — the tab row's measurement
    // pass doesn't always lay out its children in the headless Compose harness. CorpseDetails's
    // test gets away with asserting on path-segment text because that string is also rendered in
    // the body (Path / Owners labels). AppJunkPage doesn't surface the app label in the body, so
    // there's no second render to anchor against. Multi-junk pager interactions belong in
    // instrumentation tests if they're worth covering.
}

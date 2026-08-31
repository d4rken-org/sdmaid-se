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
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant

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

    // Same axis filter, but every rendered pager page contributes one vertical scrollable, so the
    // page has to be addressed by index once more than one is on screen.
    private fun ComposeContentTestRule.scrollPageTo(pageIndex: Int, text: String) {
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))[pageIndex]
            .performScrollToNode(hasText(text))
    }

    // previewExpendables() hardcodes com.example.app paths, which would collide across pages and
    // make the file row ambiguous. Each junk needs its own package-scoped cache file.
    private fun junkWithOwnCacheFile(pkgName: String, label: String): AppJunk = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = label),
        expendables = mapOf(
            DefaultCachesPublicFilter::class to listOf(
                ExpendablesFilter.Match.Deletion(
                    identifier = DefaultCachesPublicFilter::class,
                    lookup = LocalPathLookup(
                        lookedUp = LocalPath.build(
                            "storage", "emulated", "0", "Android", "data", pkgName, "cache", "blob.bin",
                        ),
                        fileType = FileType.FILE,
                        size = 6L * 1024 * 1024,
                        modifiedAt = Instant.parse("2026-04-01T12:00:00Z"),
                        target = null,
                    ),
                ),
            ),
        ),
        inaccessibleCache = null,
    )

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

    @Test
    @Config(qualifiers = "w720dp-h1024dp")
    fun `on a wide viewport the gate covers the visible neighbour page too`() {
        // spanCount is (screenWidthDp / 390 + 0.5).toInt(), so 720dp yields two pages side by side
        // and the neighbour's header card is on screen while the first page owns the selection.
        val focused = junkWithOwnCacheFile(pkgName = "com.example.focused", label = "Focused")
        val neighbour = junkWithOwnCacheFile(pkgName = "com.example.neighbour", label = "Neighbour")
        composeRule.setDetailsScreen(
            AppJunkDetailsViewModel.State(
                items = listOf(focused, neighbour),
                target = focused.identifier,
            ),
        )

        composeRule.onAllNodesWithText("Exclude").assertCountEquals(2)
        composeRule.onAllNodesWithText("Delete").assertCountEquals(2)

        val filePath = focused.expendables!!.values.first().first().path.path
        composeRule.scrollPageTo(0, filePath)
        composeRule.onNodeWithText(filePath).performTouchInput { longClick() }
        composeRule.scrollPageTo(0, "Exclude")

        // Both header cards must gate, not just the selection owner's: a page-scoped gate would
        // leave the neighbour's whole-app Delete/Exclude live.
        composeRule.onAllNodesWithText("Exclude").assertCountEquals(2)
        composeRule.onAllNodesWithText("Delete").assertCountEquals(2)
        repeat(2) { index ->
            composeRule.onAllNodesWithText("Exclude")[index].assertIsNotEnabled()
            composeRule.onAllNodesWithText("Delete")[index].assertIsNotEnabled()
        }
    }

    // NOTE: Asserting on tab/pager-only content (e.g. junk.label rendered as the tab title) is
    // brittle under Robolectric + HorizontalPager + ScrollableTabRow — the tab row's measurement
    // pass doesn't always lay out its children in the headless Compose harness. CorpseDetails's
    // test gets away with asserting on path-segment text because that string is also rendered in
    // the body (Path / Owners labels). AppJunkPage doesn't surface the app label in the body, so
    // there's no second render to anchor against. Multi-junk pager interactions belong in
    // instrumentation tests if they're worth covering.
}

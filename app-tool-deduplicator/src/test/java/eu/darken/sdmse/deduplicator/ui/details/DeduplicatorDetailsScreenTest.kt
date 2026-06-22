package eu.darken.sdmse.deduplicator.ui.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

// HorizontalPager + ScrollableTabRow are known to interact poorly with Robolectric for multi-page
// assertions. Tests here intentionally stay on single-cluster / empty state and avoid pager-swipe
// interactions or tab-switching.
class DeduplicatorDetailsScreenTest : BaseComposeRobolectricTest() {

    private fun cluster(id: String, dupeNames: List<String> = listOf("a", "b")): Duplicate.Cluster {
        val duplicates = dupeNames.map { name ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "$id-$name.jpg"),
                size = 1024L,
                hashSeed = "$id-$name",
            )
        }.toSet()
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("$id-group"),
        )
        return previewCluster(
            identifier = Duplicate.Cluster.Id(id),
            groups = setOf(group),
        )
    }

    // The screen reads `LocalGuidedTourController.current`; the CompositionLocal has no default
    // (errors if unprovided). Provide a relaxed mock that opts out of starting any tour.
    private fun mockTourController(): GuidedTourController = mockk<GuidedTourController>(relaxed = true).apply {
        coEvery { shouldStart(any()) } returns false
        every { session } returns MutableStateFlow(null)
    }

    private fun ComposeContentTestRule.setDetailsScreen(state: DeduplicatorDetailsViewModel.State?) {
        setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController()) {
                PreviewWrapper {
                    DeduplicatorDetailsScreen(stateSource = MutableStateFlow(state))
                }
            }
        }
    }

    @Test
    fun `loading state renders nothing meaningful besides the top bar title`() {
        composeRule.setDetailsScreen(null)

        // Top bar title is always visible.
        composeRule.onNodeWithText("Deduplicator").assertExists()
        // Empty placeholder must NOT render during loading.
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `empty items list shows the Empty placeholder`() {
        composeRule.setDetailsScreen(
            DeduplicatorDetailsViewModel.State(
                items = emptyList(),
                target = null,
                progress = null,
            ),
        )

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `single-cluster state renders the cluster contents — not just the top bar`() {
        val onlyCluster = cluster("vacation", dupeNames = listOf("trip", "trip-copy"))
        composeRule.setDetailsScreen(
            DeduplicatorDetailsViewModel.State(
                items = listOf(onlyCluster),
                target = onlyCluster.identifier,
                progress = null,
            ),
        )

        composeRule.onNodeWithText("Deduplicator").assertExists()
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        // The cluster summary card unconditionally renders the "Occupied storage" label — a
        // load-bearing signal that ClusterContent composed, not just the top bar.
        composeRule.onNodeWithText("Occupied storage").assertExists()
    }

    @Test
    fun `single-cluster state in directory view still renders the cluster summary card`() {
        // Switching to directory view changes the per-duplicate layout below the summary card but
        // the "Occupied storage" header stays put. Asserting on it proves the summary card is
        // intact under both view modes — a regression that gated it on flat-view would flunk.
        val onlyCluster = cluster("vacation", dupeNames = listOf("dirtest", "dirtest-copy"))
        composeRule.setDetailsScreen(
            DeduplicatorDetailsViewModel.State(
                items = listOf(onlyCluster),
                target = onlyCluster.identifier,
                progress = null,
                isDirectoryView = true,
            ),
        )

        composeRule.onNodeWithText("Occupied storage").assertExists()
    }
}

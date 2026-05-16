package eu.darken.sdmse.deduplicator.ui.list

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DeduplicatorListScreenTest : BaseComposeRobolectricTest() {

    private fun clusterRow(name: String, size: Long = 1024L): DeduplicatorListViewModel.DeduplicatorListRow {
        val duplicates = setOf(
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "$name-a.jpg"),
                size = size,
                hashSeed = "$name-a",
            ),
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "emulated", "0", "Pictures", "$name-b.jpg"),
                size = size,
                hashSeed = "$name-b",
            ),
        )
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("$name-group"),
        )
        val cluster = previewCluster(
            identifier = Duplicate.Cluster.Id(name),
            groups = setOf(group),
        )
        return DeduplicatorListViewModel.DeduplicatorListRow(
            cluster = cluster,
            deleteTargetIds = duplicates.map { it.identifier }.toSet(),
        )
    }

    private fun ComposeContentTestRule.setListScreen(state: DeduplicatorListViewModel.State?) {
        setContent {
            PreviewWrapper {
                DeduplicatorListScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    @Test
    fun `loading state shows no rows or empty marker`() {
        composeRule.setListScreen(null)

        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
        composeRule.onNodeWithText("Deduplicator").assertExists()
    }

    @Test
    fun `empty state shows the Empty placeholder`() {
        composeRule.setListScreen(
            DeduplicatorListViewModel.State(rows = emptyList(), layoutMode = LayoutMode.GRID),
        )

        composeRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun `populated GRID state shows the layout toggle action`() {
        // In GRID, the action icon is the "switch to linear" variant. Its content description
        // is `general_toggle_layout_mode` either way ("Switch view mode").
        composeRule.setListScreen(
            DeduplicatorListViewModel.State(
                rows = listOf(clusterRow("vacation")),
                layoutMode = LayoutMode.GRID,
            ),
        )

        composeRule.onNodeWithContentDescription("Switch view mode").assertExists()
        composeRule.onAllNodesWithText("Empty").assertCountEquals(0)
    }

    @Test
    fun `populated LINEAR state renders the actual row content with the duplicate file name`() {
        // Verifies LinearList composed an actual row body — not just that the empty placeholder
        // is absent. LINEAR rather than GRID because the linear row template surfaces the file
        // name as plain text; GRID uses an icon + chip layout.
        composeRule.setListScreen(
            DeduplicatorListViewModel.State(
                rows = listOf(clusterRow("vacation")),
                layoutMode = LayoutMode.LINEAR,
            ),
        )

        composeRule.onNodeWithContentDescription("Switch view mode").assertExists()
        composeRule.onNodeWithText("vacation-a.jpg").assertExists()
    }

    @Test
    fun `tapping the layout toggle invokes onToggleLayoutMode`() {
        var toggled = 0
        composeRule.setContent {
            PreviewWrapper {
                DeduplicatorListScreen(
                    stateSource = MutableStateFlow(
                        DeduplicatorListViewModel.State(
                            rows = listOf(clusterRow("vacation")),
                            layoutMode = LayoutMode.GRID,
                        ),
                    ),
                    onToggleLayoutMode = { toggled++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Switch view mode").performClick()

        // Compose state-event callbacks can fire synchronously inside an onClick lambda. Assert
        // at-least-once to stay robust to dispatcher changes.
        if (toggled < 1) throw AssertionError("Expected at least one toggle, got $toggled")
    }
}

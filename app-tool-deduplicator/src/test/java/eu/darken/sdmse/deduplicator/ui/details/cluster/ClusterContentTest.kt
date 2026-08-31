package eu.darken.sdmse.deduplicator.ui.details.cluster

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.selection.SelectionState
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumGroup
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class ClusterContentTest : BaseComposeRobolectricTest() {

    private val cluster: Duplicate.Cluster = previewCluster(groups = setOf(previewChecksumGroup()))

    // Every preview duplicate lives in the same folder, so directory view renders exactly one header.
    private val directoryLabel = "/storage/emulated/0/Pictures"

    private fun setContent(
        wholeScopeActionsEnabled: Boolean,
        isDirectoryView: Boolean = false,
        selectionEnabled: Boolean = true,
        onCollapseToggle: () -> Unit = {},
    ) {
        composeRule.setContent {
            PreviewWrapper {
                ClusterContent(
                    cluster = cluster,
                    isDirectoryView = isDirectoryView,
                    collapsed = emptySet(),
                    selection = SelectionState(),
                    selectionEnabled = selectionEnabled,
                    wholeScopeActionsEnabled = wholeScopeActionsEnabled,
                    onSelectionToggle = {},
                    onSelectionLongPress = {},
                    onCollapseToggle = { onCollapseToggle() },
                    onClusterDelete = {},
                    onClusterExclude = {},
                    onGroupDelete = {},
                    onGroupView = { _, _ -> },
                    onDuplicateDelete = {},
                    onDuplicatePreview = {},
                    onDirectoryDeleteAll = {},
                )
            }
        }
    }

    private fun ComposeContentTestRule.scrollTo(matcher: SemanticsMatcher) {
        onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
    }

    @Test
    fun `cluster and group actions are live without a selection`() {
        setContent(wholeScopeActionsEnabled = true)

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()

        composeRule.scrollTo(hasContentDescription("Delete"))
        composeRule.onNodeWithContentDescription("Delete").assertIsEnabled()
    }

    @Test
    fun `cluster and group actions are gated while a selection is active`() {
        setContent(wholeScopeActionsEnabled = false)

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()

        composeRule.scrollTo(hasContentDescription("Delete"))
        composeRule.onNodeWithContentDescription("Delete").assertIsNotEnabled()
        // Viewing a group is navigation, not destruction, so it stays available.
        composeRule.onNodeWithContentDescription("View").assertIsEnabled()
    }

    @Test
    fun `neighbour page actions are gated even though it owns no selection`() {
        // Pager neighbours render side by side but never take part in selection, so they get
        // selectionEnabled=false while the screen-global gate still applies.
        setContent(wholeScopeActionsEnabled = false, selectionEnabled = false)

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()

        composeRule.scrollTo(hasContentDescription("Delete"))
        composeRule.onNodeWithContentDescription("Delete").assertIsNotEnabled()
    }

    @Test
    fun `directory header is live without a selection`() {
        setContent(wholeScopeActionsEnabled = true, isDirectoryView = true)

        composeRule.scrollTo(hasText(directoryLabel))
        composeRule.onNode(hasClickAction() and hasText(directoryLabel)).assertIsEnabled()
    }

    @Test
    fun `directory header is gated while a selection is active`() {
        setContent(wholeScopeActionsEnabled = false, isDirectoryView = true)

        composeRule.scrollTo(hasText(directoryLabel))
        composeRule.onNode(hasClickAction() and hasText(directoryLabel)).assertIsNotEnabled()
    }

    @Test
    fun `the directory collapse toggle still fires through a gated header`() {
        var toggles = 0
        setContent(
            wholeScopeActionsEnabled = false,
            isDirectoryView = true,
            onCollapseToggle = { toggles++ },
        )

        composeRule.scrollTo(hasTestTag(ClusterContentTags.DIRECTORY_COLLAPSE))
        composeRule.onNodeWithTag(ClusterContentTags.DIRECTORY_COLLAPSE).performClick()

        composeRule.runOnIdle { assertEquals(1, toggles) }
    }
}

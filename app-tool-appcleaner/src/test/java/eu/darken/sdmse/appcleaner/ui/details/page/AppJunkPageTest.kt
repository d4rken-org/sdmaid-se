package eu.darken.sdmse.appcleaner.ui.details.page

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.selection.SelectionState
import eu.darken.sdmse.common.files.APath
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AppJunkPageTest : BaseComposeRobolectricTest() {

    private val junk: AppJunk = previewAppJunk()
    private val categoryLabel = "Public default caches"
    private val inaccessibleLabel = "Default caches"

    private fun setPage(
        wholeScopeActionsEnabled: Boolean,
        isCurrentPage: Boolean = true,
        selected: Set<APath> = emptySet(),
        onToggleCollapse: () -> Unit = {},
    ) {
        composeRule.setContent {
            PreviewWrapper {
                AppJunkPage(
                    junk = junk,
                    collapsed = emptySet(),
                    selection = SelectionState(initial = selected),
                    isCurrentPage = isCurrentPage,
                    wholeScopeActionsEnabled = wholeScopeActionsEnabled,
                    onDeleteJunk = {},
                    onExcludeJunk = {},
                    onDeleteInaccessible = {},
                    onDeleteCategory = {},
                    onDeleteFile = { _, _ -> },
                    onToggleCollapse = { onToggleCollapse() },
                )
            }
        }
    }

    private fun ComposeContentTestRule.scrollTo(text: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    private fun selectedPath(): APath = junk.expendables!!.values.first().first().path

    @Test
    fun `whole-scope affordances are live without a selection`() {
        setPage(wholeScopeActionsEnabled = true)

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()

        composeRule.scrollTo(inaccessibleLabel)
        composeRule.onNode(hasClickAction() and hasText(inaccessibleLabel)).assertIsEnabled()

        composeRule.scrollTo(categoryLabel)
        composeRule.onNode(hasClickAction() and hasText(categoryLabel)).assertIsEnabled()
    }

    @Test
    fun `whole-scope affordances are gated while a selection is active`() {
        setPage(wholeScopeActionsEnabled = false, selected = setOf(selectedPath()))

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()

        composeRule.scrollTo(inaccessibleLabel)
        composeRule.onNode(hasClickAction() and hasText(inaccessibleLabel)).assertIsNotEnabled()

        composeRule.scrollTo(categoryLabel)
        composeRule.onNode(hasClickAction() and hasText(categoryLabel)).assertIsNotEnabled()
    }

    @Test
    fun `neighbour page affordances are gated even though it owns no selection`() {
        // Pager neighbours render side by side but never take part in selection, so they get
        // isCurrentPage=false while the screen-global gate still applies.
        setPage(wholeScopeActionsEnabled = false, isCurrentPage = false)

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()

        composeRule.scrollTo(categoryLabel)
        composeRule.onNode(hasClickAction() and hasText(categoryLabel)).assertIsNotEnabled()
    }

    @Test
    fun `the category collapse toggle still fires through a gated card`() {
        var toggles = 0
        setPage(wholeScopeActionsEnabled = false, onToggleCollapse = { toggles++ })

        composeRule.scrollTo(categoryLabel)
        composeRule.onNodeWithTag(AppJunkPageTags.CATEGORY_COLLAPSE).performClick()

        composeRule.runOnIdle { assertEquals(1, toggles) }
    }
}

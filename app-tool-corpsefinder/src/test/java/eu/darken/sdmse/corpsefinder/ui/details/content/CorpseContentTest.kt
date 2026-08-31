package eu.darken.sdmse.corpsefinder.ui.details.content

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.selection.SelectionState
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class CorpseContentTest : BaseComposeRobolectricTest() {

    private val corpse: Corpse = previewCorpse()

    private fun setContent(
        wholeScopeActionsEnabled: Boolean,
        pageSelection: SelectionState<APath>?,
    ) {
        composeRule.setContent {
            PreviewWrapper {
                CorpseContent(
                    corpse = corpse,
                    pageSelection = pageSelection,
                    wholeScopeActionsEnabled = wholeScopeActionsEnabled,
                    onDeleteCorpseRequest = {},
                    onExcludeRequest = {},
                    onFileTap = {},
                )
            }
        }
    }

    @Test
    fun `header actions are live without a selection`() {
        setContent(wholeScopeActionsEnabled = true, pageSelection = SelectionState())

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()
    }

    @Test
    fun `header actions are gated while a selection is active`() {
        val selected = corpse.content.first().lookedUp
        setContent(
            wholeScopeActionsEnabled = false,
            pageSelection = SelectionState(initial = setOf(selected)),
        )

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun `neighbour page header actions are gated even though it owns no selection`() {
        // Pager neighbours render side by side but are handed a null selection holder, so a
        // page-local predicate would read as "nothing selected here" and leave them live.
        setContent(wholeScopeActionsEnabled = false, pageSelection = null)

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }
}

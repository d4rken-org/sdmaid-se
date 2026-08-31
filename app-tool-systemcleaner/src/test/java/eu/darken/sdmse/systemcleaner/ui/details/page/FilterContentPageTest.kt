package eu.darken.sdmse.systemcleaner.ui.details.page

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.selection.SelectionState
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.ui.preview.previewFilterContent
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class FilterContentPageTest : BaseComposeRobolectricTest() {

    private val filterContent: FilterContent = previewFilterContent()

    private fun setPage(
        wholeScopeActionsEnabled: Boolean,
        selectionEnabled: Boolean = true,
        selected: Set<APath> = emptySet(),
    ) {
        composeRule.setContent {
            PreviewWrapper {
                FilterContentPage(
                    filterContent = filterContent,
                    selection = SelectionState(initial = selected),
                    selectionEnabled = selectionEnabled,
                    wholeScopeActionsEnabled = wholeScopeActionsEnabled,
                    onDeleteFilterRequest = {},
                    onExcludeFilterRequest = {},
                    onFileTap = {},
                    onPreviewFile = {},
                )
            }
        }
    }

    @Test
    fun `header actions are live without a selection`() {
        setPage(wholeScopeActionsEnabled = true)

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()
    }

    @Test
    fun `header actions are gated while a selection is active`() {
        setPage(
            wholeScopeActionsEnabled = false,
            selected = setOf(filterContent.items.first().path),
        )

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun `neighbour page header actions are gated even though it owns no selection`() {
        // Pager neighbours render side by side but never take part in selection, so they get
        // selectionEnabled=false while the screen-global gate still applies.
        setPage(
            wholeScopeActionsEnabled = false,
            selectionEnabled = false,
        )

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }
}

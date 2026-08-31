package eu.darken.sdmse.common.compose.layout

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SdmWholeScopeActionsTest : BaseComposeRobolectricTest() {

    private fun setActions(enabled: Boolean) {
        composeRule.setContent {
            PreviewWrapper {
                SdmWholeScopeActions(
                    enabled = enabled,
                    onExclude = {},
                    onDelete = {},
                )
            }
        }
    }

    @Test
    fun `both actions are enabled when the flag is set`() {
        setActions(enabled = true)

        composeRule.onNodeWithText("Exclude").assertIsEnabled()
        composeRule.onNodeWithText("Delete").assertIsEnabled()
    }

    @Test
    fun `both actions are disabled when the flag is cleared`() {
        setActions(enabled = false)

        composeRule.onNodeWithText("Exclude").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }
}

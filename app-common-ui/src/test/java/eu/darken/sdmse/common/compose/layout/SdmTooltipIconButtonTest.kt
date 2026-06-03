package eu.darken.sdmse.common.compose.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SdmTooltipIconButtonTest : BaseComposeRobolectricTest() {

    @Test
    fun `tap invokes onClick`() {
        var clicks = 0
        composeRule.setContent {
            PreviewWrapper {
                SdmTooltipIconButton(
                    icon = Icons.TwoTone.Delete,
                    label = "Delete files",
                    onClick = { clicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Delete files").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun `long-press shows the tooltip without invoking onClick`() {
        var clicks = 0
        composeRule.setContent {
            PreviewWrapper {
                SdmTooltipIconButton(
                    icon = Icons.TwoTone.Delete,
                    label = "Delete files",
                    onClick = { clicks++ },
                )
            }
        }

        // The TooltipBox consumes the long press to show the cheat-sheet; it must NOT also click.
        composeRule.onNodeWithContentDescription("Delete files").performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun `disabled button is not enabled`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmTooltipIconButton(
                    icon = Icons.TwoTone.Delete,
                    label = "Delete files",
                    onClick = {},
                    enabled = false,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Delete files").assertIsNotEnabled()
    }

    @Test
    fun `label is exposed as the content description`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmTooltipIconButton(
                    icon = Icons.TwoTone.Delete,
                    label = "Delete files",
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Delete files").assertExists()
    }
}

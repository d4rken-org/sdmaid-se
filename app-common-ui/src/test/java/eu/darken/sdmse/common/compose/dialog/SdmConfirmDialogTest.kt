package eu.darken.sdmse.common.compose.dialog

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SdmConfirmDialogTest : BaseComposeRobolectricTest() {

    private val title = "Confirm delete"
    private val message = "Are you sure?"
    private val positiveLabel = "Delete"
    private val negativeLabel = "Cancel"
    private val neutralLabel = "Show details"

    @Test
    fun `title and message render when both provided`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                )
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun `title is omitted when null`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                )
            }
        }

        composeRule.onAllNodesWithText(title).assertCountEquals(0)
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun `with all three actions, neutral is on the leading edge and negative-positive on the trailing edge`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                    negative = SdmDialogAction(label = negativeLabel, onClick = {}),
                    neutral = SdmDialogAction(label = neutralLabel, onClick = {}),
                )
            }
        }

        val neutralX = composeRule.onNodeWithText(neutralLabel).fetchSemanticsNode().positionInRoot.x
        val negativeX = composeRule.onNodeWithText(negativeLabel).fetchSemanticsNode().positionInRoot.x
        val positiveX = composeRule.onNodeWithText(positiveLabel).fetchSemanticsNode().positionInRoot.x

        assertTrue("neutral ($neutralX) should sit left of negative ($negativeX)", neutralX < negativeX)
        assertTrue("negative ($negativeX) should sit left of positive ($positiveX)", negativeX < positiveX)
    }

    @Test
    fun `with negative and positive only, negative sits left of positive`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                    negative = SdmDialogAction(label = negativeLabel, onClick = {}),
                )
            }
        }

        val negativeX = composeRule.onNodeWithText(negativeLabel).fetchSemanticsNode().positionInRoot.x
        val positiveX = composeRule.onNodeWithText(positiveLabel).fetchSemanticsNode().positionInRoot.x

        assertTrue("negative ($negativeX) should sit left of positive ($positiveX)", negativeX < positiveX)
        composeRule.onAllNodesWithText(neutralLabel).assertCountEquals(0)
    }

    @Test
    fun `with only positive, no other buttons render`() {
        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                )
            }
        }

        composeRule.onNodeWithText(positiveLabel).assertIsDisplayed()
        composeRule.onAllNodesWithText(negativeLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText(neutralLabel).assertCountEquals(0)
    }

    @Test
    fun `tapping positive invokes the positive callback`() {
        var positiveClicks = 0
        var negativeClicks = 0
        var neutralClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel) { positiveClicks++ },
                    negative = SdmDialogAction(label = negativeLabel) { negativeClicks++ },
                    neutral = SdmDialogAction(label = neutralLabel) { neutralClicks++ },
                )
            }
        }

        composeRule.onNodeWithText(positiveLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(1, positiveClicks)
            assertEquals(0, negativeClicks)
            assertEquals(0, neutralClicks)
        }
    }

    @Test
    fun `tapping negative invokes the negative callback`() {
        var positiveClicks = 0
        var negativeClicks = 0
        var neutralClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel) { positiveClicks++ },
                    negative = SdmDialogAction(label = negativeLabel) { negativeClicks++ },
                    neutral = SdmDialogAction(label = neutralLabel) { neutralClicks++ },
                )
            }
        }

        composeRule.onNodeWithText(negativeLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(0, positiveClicks)
            assertEquals(1, negativeClicks)
            assertEquals(0, neutralClicks)
        }
    }

    @Test
    fun `tapping neutral invokes the neutral callback`() {
        var positiveClicks = 0
        var negativeClicks = 0
        var neutralClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel) { positiveClicks++ },
                    negative = SdmDialogAction(label = negativeLabel) { negativeClicks++ },
                    neutral = SdmDialogAction(label = neutralLabel) { neutralClicks++ },
                )
            }
        }

        composeRule.onNodeWithText(neutralLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(0, positiveClicks)
            assertEquals(0, negativeClicks)
            assertEquals(1, neutralClicks)
        }
    }

    @Test
    fun `disabled positive cannot be clicked`() {
        var positiveClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(
                        label = positiveLabel,
                        enabled = false,
                    ) { positiveClicks++ },
                    negative = SdmDialogAction(label = negativeLabel, onClick = {}),
                )
            }
        }

        composeRule.onNodeWithText(positiveLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(negativeLabel).assertIsEnabled()
        composeRule.onNodeWithText(positiveLabel).performClick()
        composeRule.runOnIdle { assertEquals(0, positiveClicks) }
    }

    @Test
    fun `disabled negative cannot be clicked`() {
        var negativeClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                    negative = SdmDialogAction(
                        label = negativeLabel,
                        enabled = false,
                    ) { negativeClicks++ },
                )
            }
        }

        composeRule.onNodeWithText(negativeLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(negativeLabel).performClick()
        composeRule.runOnIdle { assertEquals(0, negativeClicks) }
    }

    @Test
    fun `disabled neutral cannot be clicked`() {
        var neutralClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                    neutral = SdmDialogAction(
                        label = neutralLabel,
                        enabled = false,
                    ) { neutralClicks++ },
                )
            }
        }

        composeRule.onNodeWithText(neutralLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(neutralLabel).performClick()
        composeRule.runOnIdle { assertEquals(0, neutralClicks) }
    }

    @Test
    fun `confirm key held from before the dialog opened does not click on release`() {
        var positiveClicks = 0

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    message = message,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel) { positiveClicks++ },
                )
            }
        }

        val button = composeRule.onNodeWithText(positiveLabel)
        button.requestFocus()
        composeRule.waitForIdle()

        // A D-pad long press that opened this dialog is still held: the dialog window only ever
        // sees framework auto-repeats (repeatCount > 0) followed by the release.
        button.performDpadCenter(action = NativeKeyEvent.ACTION_DOWN, repeat = 1)
        button.performDpadCenter(action = NativeKeyEvent.ACTION_UP)
        composeRule.runOnIdle { assertEquals(0, positiveClicks) }

        // A fresh press afterwards must still click.
        button.performDpadCenter(action = NativeKeyEvent.ACTION_DOWN)
        button.performDpadCenter(action = NativeKeyEvent.ACTION_UP)
        composeRule.runOnIdle { assertEquals(1, positiveClicks) }
    }

    private fun SemanticsNodeInteraction.performDpadCenter(action: Int, repeat: Int = 0) {
        performKeyPress(
            ComposeKeyEvent(
                NativeKeyEvent(0L, 0L, action, NativeKeyEvent.KEYCODE_DPAD_CENTER, repeat),
            ),
        )
    }

    @Test
    fun `custom content slot renders inside the dialog`() {
        val contentTag = "custom-content"
        val contentText = "Custom dialog body"

        composeRule.setContent {
            PreviewWrapper {
                SdmConfirmDialog(
                    title = title,
                    onDismissRequest = {},
                    positive = SdmDialogAction(label = positiveLabel, onClick = {}),
                ) {
                    Text(text = contentText, modifier = Modifier.testTag(contentTag))
                }
            }
        }

        composeRule.onNodeWithTag(contentTag).assertIsDisplayed()
        composeRule.onNodeWithText(contentText).assertIsDisplayed()
    }
}

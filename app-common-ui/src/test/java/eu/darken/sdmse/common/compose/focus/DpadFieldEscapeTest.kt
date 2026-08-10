package eu.darken.sdmse.common.compose.focus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Duration

/**
 * A focused text field consumes D-pad UP/DOWN for caret movement. Remotes have no TAB key, so a
 * dialog field that holds focus without [dpadVerticalFieldEscape] is a dead end on TV: the buttons
 * below it are unreachable.
 */
@OptIn(ExperimentalTestApi::class)
class DpadFieldEscapeTest : BaseComposeRobolectricTest() {

    private val aboveTag = "above"
    private val belowTag = "below"

    private fun field() = composeRule.onNode(hasSetTextAction())

    @Composable
    private fun Harness(escape: Boolean) {
        var value by remember { mutableStateOf("") }
        Column {
            Box(Modifier.size(10.dp).focusable().testTag(aboveTag))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = if (escape) Modifier.dpadVerticalFieldEscape() else Modifier,
            )
            Box(Modifier.size(10.dp).focusable().testTag(belowTag))
        }
    }

    private fun setHarness(escape: Boolean) {
        composeRule.setContent { PreviewWrapper { Harness(escape = escape) } }
        field().requestFocus()
        field().assertIsFocused()
    }

    @Test
    fun `dpad down moves focus out of the field`() {
        setHarness(escape = true)

        field().performKeyInput { pressKey(Key.DirectionDown) }

        field().assertIsNotFocused()
        composeRule.onNodeWithTag(belowTag).assertIsFocused()
    }

    @Test
    fun `dpad up moves focus out of the field`() {
        setHarness(escape = true)

        field().performKeyInput { pressKey(Key.DirectionUp) }

        field().assertIsNotFocused()
        composeRule.onNodeWithTag(aboveTag).assertIsFocused()
    }

    /**
     * The control: proves the two tests above are not vacuous. Without the modifier the field eats
     * the key and focus never leaves — exactly the trap reported from the device.
     */
    @Test
    fun `without the escape the field swallows the vertical keys`() {
        setHarness(escape = false)

        field().performKeyInput { pressKey(Key.DirectionDown) }
        field().assertIsFocused()

        field().performKeyInput { pressKey(Key.DirectionUp) }
        field().assertIsFocused()
    }

    @Test
    fun `the size dialog's field releases dpad down`() {
        composeRule.setContent {
            PreviewWrapper {
                SizeInputDialog(
                    titleRes = R.string.general_save_action,
                    currentSize = 16 * 1024L,
                    onSave = {},
                    onReset = {},
                    onDismiss = {},
                )
            }
        }
        field().requestFocus()
        field().assertIsFocused()

        field().performKeyInput { pressKey(Key.DirectionDown) }

        field().assertIsNotFocused()
    }

    @Test
    fun `the age dialog's field releases dpad down`() {
        composeRule.setContent {
            PreviewWrapper {
                AgeInputDialog(
                    titleRes = R.string.general_save_action,
                    currentAge = Duration.ofDays(3),
                    onSave = {},
                    onReset = {},
                    onDismiss = {},
                )
            }
        }
        field().requestFocus()
        field().assertIsFocused()

        field().performKeyInput { pressKey(Key.DirectionDown) }

        field().assertIsNotFocused()
    }
}

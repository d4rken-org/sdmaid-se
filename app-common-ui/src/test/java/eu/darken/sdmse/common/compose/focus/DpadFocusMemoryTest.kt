package eu.darken.sdmse.common.compose.focus

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isNotFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * D-pad focus memory across navigation: the harness simulates Nav3's push/pop by swapping the
 * settings content out of and back into composition under a [rememberSaveableStateHolder] —
 * the same mechanism `rememberSaveableStateHolderNavEntryDecorator` uses, so saveable state
 * (the remembered focus key) survives while everything `remember`-ed is rebuilt.
 */
class DpadFocusMemoryTest : BaseComposeRobolectricTest() {

    @Test
    fun `remembered row regains focus after content recreation in keyboard mode`() {
        val harness = Harness(inputMode = InputMode.Keyboard)
        composeRule.setHarnessContent(harness)

        composeRule.onNodeWithText("Item 2").requestFocus()
        composeRule.waitForIdle()

        harness.recreateScreen(composeRule)

        composeRule.onNodeWithText("Item 2").assert(isFocused())
    }

    @Test
    fun `touch input mode suppresses focus restoration`() {
        val harness = Harness(inputMode = InputMode.Touch)
        composeRule.setHarnessContent(harness)

        composeRule.onNodeWithText("Item 2").requestFocus()
        composeRule.waitForIdle()

        harness.recreateScreen(composeRule)

        composeRule.onNodeWithText("Item 2").assert(isNotFocused())
    }

    @Test
    fun `a row that vanished while away cannot steal focus when it reappears later`() {
        val harness = Harness(inputMode = InputMode.Keyboard)
        composeRule.setHarnessContent(harness)

        composeRule.onNodeWithText("Item 2").requestFocus()
        composeRule.waitForIdle()

        // The remembered row is gone after recreation (conditional row toggled off while the
        // screen was away) — the pending restore must expire, not lie in wait.
        harness.rowTitles = ROW_TITLES - "Item 2"
        harness.recreateScreen(composeRule)

        harness.rowTitles = ROW_TITLES
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 2").assert(isNotFocused())
    }

    @Test
    fun `focus moved to another row updates the memory`() {
        val harness = Harness(inputMode = InputMode.Keyboard)
        composeRule.setHarnessContent(harness)

        composeRule.onNodeWithText("Item 2").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Item 4").requestFocus()
        composeRule.waitForIdle()

        harness.recreateScreen(composeRule)

        composeRule.onNodeWithText("Item 4").assert(isFocused())
        composeRule.onNodeWithText("Item 2").assert(isNotFocused())
    }

    companion object {
        private val ROW_TITLES = (0..5).map { "Item $it" }
    }

    private class Harness(val inputMode: InputMode) {
        var showScreen by mutableStateOf(true)
        var rowTitles by mutableStateOf(ROW_TITLES)

        fun recreateScreen(composeRule: ComposeContentTestRule) {
            showScreen = false
            composeRule.waitForIdle()
            showScreen = true
            composeRule.waitForIdle()
        }
    }

    private fun ComposeContentTestRule.setHarnessContent(harness: Harness) {
        setContent {
            PreviewWrapper {
                CompositionLocalProvider(
                    LocalInputModeManager provides FakeInputModeManager(harness.inputMode),
                ) {
                    val holder = rememberSaveableStateHolder()
                    if (harness.showScreen) {
                        holder.SaveableStateProvider("settings") {
                            TestSettingsScreen(rowTitles = harness.rowTitles)
                        }
                    } else {
                        holder.SaveableStateProvider("sub-screen") {
                            Text("Sub screen")
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private class FakeInputModeManager(override val inputMode: InputMode) : InputModeManager {
        override fun requestInputMode(inputMode: InputMode): Boolean = false
    }
}

@Composable
private fun TestSettingsScreen(rowTitles: List<String>) {
    SdmScaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            items(rowTitles.size, key = { rowTitles[it] }) { index ->
                SettingsPreferenceItem(
                    title = rowTitles[index],
                    onClick = {},
                )
            }
        }
    }
}

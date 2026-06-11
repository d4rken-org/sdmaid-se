package eu.darken.sdmse.common.compose.layout

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * D-pad bridges on [SdmScaffold]: every chrome slot (top bar, FAB, snackbar) is a focus pocket
 * by geometry — the full-window content focus group contains the chrome rects, so directional
 * search alone can never move from chrome into content. These tests pin the custom-destination
 * bridges and the natural (geometric) reverse paths.
 */
class SdmScaffoldDpadTest : BaseComposeRobolectricTest() {

    @Test
    fun `DOWN from the top bar back button enters the content list`() {
        composeRule.setScaffoldContent()
        composeRule.onNodeWithContentDescription(BACK_LABEL).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_DOWN)

        composeRule.assertFocusedWithin(hasText("Row 0"))
    }

    @Test
    fun `UP from the first content row reaches the top bar`() {
        composeRule.setScaffoldContent()
        composeRule.onNodeWithText("Row 0").requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasContentDescription(BACK_LABEL))
    }

    @Test
    fun `UP from the FAB enters the content list`() {
        composeRule.setScaffoldContent(showFab = true)
        composeRule.onNodeWithContentDescription(FAB_LABEL).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasText("Row", substring = true))
    }

    @Test
    fun `UP from a snackbar action enters the content list`() {
        composeRule.setScaffoldContent(showSnackbar = true)
        composeRule.onNodeWithText(SNACKBAR_ACTION).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasText("Row", substring = true))
    }

    @Test
    fun `DOWN from the top bar with empty content keeps focus stable in the top bar`() {
        composeRule.setScaffoldContent(rowCount = 0)
        composeRule.onNodeWithContentDescription(BACK_LABEL).requestFocus()
        composeRule.waitForIdle()

        // The bridge targets a content group with no focusable children — must not crash,
        // and the user must not lose their focus position.
        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_DOWN)

        composeRule.assertFocusedWithin(hasContentDescription(BACK_LABEL))
    }

    @Test
    fun `DOWN from the top bar with empty content reaches the FAB`() {
        // Exclusion-manager scenario: fresh screen shows an empty state (nothing focusable in
        // content) and the FAB is the only way to create an entry — the bridge must fall back
        // to geometric search instead of swallowing the key.
        composeRule.setScaffoldContent(rowCount = 0, showFab = true)
        composeRule.onNodeWithContentDescription(BACK_LABEL).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_DOWN)

        composeRule.assertFocusedWithin(hasContentDescription(FAB_LABEL))
    }

    @Test
    fun `UP from the FAB with empty content reaches the top bar`() {
        composeRule.setScaffoldContent(rowCount = 0, showFab = true)
        composeRule.onNodeWithContentDescription(FAB_LABEL).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasContentDescription(BACK_LABEL))
    }

    @Test
    fun `UP from a snackbar action with empty content reaches the top bar`() {
        // E.g. removing the last list entry shows an indefinite Undo snackbar over an
        // empty screen — focus must still be able to leave the snackbar.
        composeRule.setScaffoldContent(rowCount = 0, showSnackbar = true)
        composeRule.onNodeWithText(SNACKBAR_ACTION).requestFocus()
        composeRule.waitForIdle()

        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_UP)

        composeRule.assertFocusedWithin(hasContentDescription(BACK_LABEL))
    }

    @Test
    fun `content appearing after a loading state is reachable from the top bar`() {
        val rowCount = androidx.compose.runtime.mutableIntStateOf(0)
        composeRule.setContent {
            PreviewWrapper { TestScreen(rowCount = rowCount.intValue) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(BACK_LABEL).requestFocus()
        composeRule.waitForIdle()

        rowCount.intValue = 3
        composeRule.waitForIdle()
        composeRule.pressKey(NativeKeyEvent.KEYCODE_DPAD_DOWN)

        composeRule.assertFocusedWithin(hasText("Row 0"))
    }

    companion object {
        private const val BACK_LABEL = "Back"
        private const val FAB_LABEL = "Add"
        private const val SNACKBAR_ACTION = "Retry"
    }
}

@Composable
private fun TestScreen(
    rowCount: Int = 3,
    showFab: Boolean = false,
    showSnackbar: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    if (showSnackbar) {
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar(
                message = "Something happened",
                actionLabel = "Retry",
                duration = SnackbarDuration.Indefinite,
            )
        }
    }
    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Title") },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = "Back",
                        onClick = {},
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = {}) {
                    Icon(Icons.TwoTone.Add, contentDescription = "Add")
                }
            }
        },
    ) { paddingValues ->
        if (rowCount == 0) {
            // Loading/empty state: nothing focusable in the content slot.
            Box(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                items(rowCount) { index ->
                    Text(
                        text = "Row $index",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {}
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

private fun ComposeContentTestRule.setScaffoldContent(
    rowCount: Int = 3,
    showFab: Boolean = false,
    showSnackbar: Boolean = false,
) {
    setContent {
        PreviewWrapper {
            TestScreen(
                rowCount = rowCount,
                showFab = showFab,
                showSnackbar = showSnackbar,
            )
        }
    }
    waitForIdle()
}

// Dispatched to the focused node, not onRoot(): focusing a tooltip-wrapped button opens its
// tooltip popup, which adds a second root and makes onRoot() ambiguous.
private fun ComposeContentTestRule.pressKey(keyCode: Int) {
    onNode(isFocused()).performKeyPress(ComposeKeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_DOWN, keyCode)))
    onNode(isFocused()).performKeyPress(ComposeKeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_UP, keyCode)))
    waitForIdle()
}

/**
 * Asserts that *some* node is focused and that it is, or sits inside/contains, a node matching
 * [matcher] — focus may land on a container or a child action depending on traversal heuristics.
 */
private fun ComposeContentTestRule.assertFocusedWithin(matcher: SemanticsMatcher) {
    onNode(isFocused()).assert(matcher.or(hasAnyAncestor(matcher)).or(hasAnyDescendant(matcher)))
}

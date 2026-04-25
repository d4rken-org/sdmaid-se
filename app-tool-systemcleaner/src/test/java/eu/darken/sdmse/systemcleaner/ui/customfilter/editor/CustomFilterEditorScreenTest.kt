package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class CustomFilterEditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `loading state shows progress indicator and no save button`() {
        composeRule.setEditorContent {
            CustomFilterEditorScreen(stateSource = MutableStateFlow(null))
        }

        // Save action is gated on state.canSave, never visible while state is null.
        composeRule.onAllNodesWithContentDescription("Save").assertCountEquals(0)
    }

    @Test
    fun `populated edit-existing state surfaces the label as toolbar subtitle`() {
        val state = CustomFilterEditorViewModel.State(
            original = CustomFilterConfig(identifier = "abc", label = "Old downloads"),
            current = CustomFilterConfig(identifier = "abc", label = "Old downloads"),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(stateSource = MutableStateFlow(state))
        }

        // Two text nodes: the title "Custom filter" + the subtitle "Old downloads".
        composeRule.onAllNodesWithText("Old downloads").assertCountEquals(2)
    }

    @Test
    fun `canSave true exposes Save action and canRemove true exposes Remove action`() {
        // canSave requires (a) original != current, (b) !current.isUnderdefined (path or name criteria),
        // (c) current.label.isNotEmpty(). Give the current config one path criterium and a label edit.
        val basePath = SegmentCriterium(
            segments = listOf("Downloads"),
            mode = SegmentCriterium.Mode.Contain(allowPartial = true),
        )
        val state = CustomFilterEditorViewModel.State(
            original = CustomFilterConfig(
                identifier = "abc",
                label = "name",
                pathCriteria = setOf(basePath),
            ),
            current = CustomFilterConfig(
                identifier = "abc",
                label = "edited",
                pathCriteria = setOf(basePath),
            ),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(stateSource = MutableStateFlow(state))
        }

        composeRule.onAllNodesWithContentDescription("Save").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Remove").assertCountEquals(1)
    }

    @Test
    fun `new-filter state hides Remove action and gates Save until label is set`() {
        // Empty label ⇒ canSave = false ⇒ no Save action.
        val emptyState = CustomFilterEditorViewModel.State(
            original = null,
            current = CustomFilterConfig(identifier = "abc", label = ""),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(stateSource = MutableStateFlow(emptyState))
        }
        composeRule.onAllNodesWithContentDescription("Remove").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Save").assertCountEquals(0)
    }
}

private fun ComposeContentTestRule.setEditorContent(content: @Composable () -> Unit) {
    setContent { PreviewWrapper { content() } }
}

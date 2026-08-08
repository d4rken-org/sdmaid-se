package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class CustomFilterEditorScreenTest : BaseComposeRobolectricTest() {

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

    @Test
    fun `tapping Save invokes onSave when canSave is true`() {
        var saveClicks = 0
        val basePath = SegmentCriterium(
            segments = listOf("Downloads"),
            mode = SegmentCriterium.Mode.Contain(allowPartial = true),
        )
        val state = CustomFilterEditorViewModel.State(
            original = CustomFilterConfig(identifier = "abc", label = "old", pathCriteria = setOf(basePath)),
            current = CustomFilterConfig(identifier = "abc", label = "new", pathCriteria = setOf(basePath)),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(
                stateSource = MutableStateFlow(state),
                onSave = { saveClicks++ },
            )
        }

        composeRule.onNodeWithContentDescription("Save").performClick()

        if (saveClicks != 1) throw AssertionError("Expected onSave to fire once, got $saveClicks")
    }

    @Test
    fun `tapping Remove invokes onRemove when canRemove is true`() {
        var removeClicks = 0
        val basePath = SegmentCriterium(
            segments = listOf("Downloads"),
            mode = SegmentCriterium.Mode.Contain(allowPartial = true),
        )
        val state = CustomFilterEditorViewModel.State(
            original = CustomFilterConfig(identifier = "abc", label = "old", pathCriteria = setOf(basePath)),
            current = CustomFilterConfig(identifier = "abc", label = "old", pathCriteria = setOf(basePath)),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(
                stateSource = MutableStateFlow(state),
                onRemove = { removeClicks++ },
            )
        }

        composeRule.onNodeWithContentDescription("Remove").performClick()

        if (removeClicks != 1) throw AssertionError("Expected onRemove to fire once, got $removeClicks")
    }

    @Test
    fun `current config label renders as toolbar subtitle when non-blank for new filter too`() {
        // Documents that the subtitle gating is on `current.label.isNotBlank()`, regardless
        // of whether this is an edit-existing (original != null) or new (original == null) flow.
        val state = CustomFilterEditorViewModel.State(
            original = null,
            current = CustomFilterConfig(identifier = "new-filter", label = "Typed name"),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(stateSource = MutableStateFlow(state))
        }

        // The label appears as the toolbar subtitle.
        composeRule.onAllNodesWithText("Typed name").fetchSemanticsNodes().size.let {
            if (it == 0) throw AssertionError("Expected typed label visible as subtitle for new filter")
        }
    }

    @Test
    fun `collapsed sheet peek exposes the whole live-search summary`() {
        val state = CustomFilterEditorViewModel.State(
            original = null,
            current = CustomFilterConfig(identifier = "abc", label = "Test"),
        )
        composeRule.setEditorContent {
            CustomFilterEditorScreen(
                stateSource = MutableStateFlow(state),
                liveSearchSource = MutableStateFlow(
                    CustomFilterEditorViewModel.LiveSearchState(firstInit = true),
                ),
            )
        }

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        // firstInit renders "Live search" over "Ready"; "Ready" is the lowest element of the summary row.
        val summary = composeRule.onNodeWithText("Ready").getUnclippedBoundsInRoot()

        // The peek must cover drag handle + summary. Under the old 64dp peek the summary's bottom sat
        // ~36dp below the window edge; 1dp of tolerance separates that from the exact-fit success case.
        // Window insets are zero under Robolectric, so this covers the drag-handle/summary half of
        // the peek calculation only.
        if (summary.bottom.value >= root.height.value + 1f) {
            throw AssertionError(
                "Live search summary is clipped by the collapsed sheet peek: " +
                    "summary bottom=${summary.bottom}, window height=${root.height}",
            )
        }
    }
}

private fun ComposeContentTestRule.setEditorContent(content: @Composable () -> Unit) {
    setContent { PreviewWrapper { content() } }
}

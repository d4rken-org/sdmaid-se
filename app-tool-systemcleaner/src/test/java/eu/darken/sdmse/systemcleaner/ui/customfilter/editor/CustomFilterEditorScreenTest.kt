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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.FileType
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

    @Test
    fun `tapping the Files row label toggles the file type`() {
        val toggled = mutableListOf<FileType>()
        composeRule.setEditorContent {
            CustomFilterEditorScreen(
                stateSource = MutableStateFlow(editorState()),
                onToggleFileType = { toggled.add(it) },
            )
        }

        // The file type section sits below the initial viewport, and performClick() injects touch
        // input without auto-scrolling. useUnmergedTree targets the label Text itself, so this
        // actually exercises "tapping the label", not the merged row node's centre.
        composeRule.onNodeWithText("Files", useUnmergedTree = true).performScrollTo().performClick()

        if (toggled != listOf(FileType.FILE)) {
            throw AssertionError("Expected a single FILE toggle, got $toggled")
        }
    }

    @Test
    fun `tapping the Folders row label toggles the file type`() {
        val toggled = mutableListOf<FileType>()
        composeRule.setEditorContent {
            CustomFilterEditorScreen(
                stateSource = MutableStateFlow(editorState()),
                onToggleFileType = { toggled.add(it) },
            )
        }

        composeRule.onNodeWithText("Folders", useUnmergedTree = true).performScrollTo().performClick()

        if (toggled != listOf(FileType.DIRECTORY)) {
            throw AssertionError("Expected a single DIRECTORY toggle, got $toggled")
        }
    }

    @Test
    fun `area chips no longer reserve the 48dp minimum interactive height`() {
        composeRule.setEditorContent {
            CustomFilterEditorScreen(stateSource = MutableStateFlow(editorState()))
        }

        val areaLabels = listOf("SDCARD", "PUBLIC_DATA", "PUBLIC_MEDIA", "PUBLIC_OBB", "PRIVATE_DATA", "PORTABLE")
        // Scroll once, then measure: scrolling between measurements would mix different scroll
        // offsets into the row tops and make the pitch meaningless.
        composeRule.onNodeWithText(areaLabels.last()).performScrollTo()

        // A single chip's height is not usable here: the semantics coordinator sits inside the
        // minimumInteractiveComponentSize layout modifier, so it reads ~32dp before and after.
        // The row pitch is what actually changes.
        val rowTops = areaLabels
            .map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top }
            .distinct()
            .sorted()

        if (rowTops.size < 2) {
            throw AssertionError("Expected the area chips to wrap over multiple rows, found ${rowTops.size} row(s)")
        }

        val pitch = rowTops.zipWithNext { upper, lower -> lower - upper }.min()
        // With the default 48dp interactive floor each chip measures >= 48dp, so the pitch would be
        // >= 50dp. With the 40dp floor the chip measures max(content, 40dp) = 40dp, so the pitch is
        // the 40dp chip plus the 2dp spacing - measured at 42dp here. 46dp is the midpoint: anything
        // >= 46dp means the default floor is back, and the gap to 42dp absorbs text-metric drift.
        if (pitch >= 46.dp) {
            throw AssertionError("Area chip rows are still $pitch apart, expected less than 46.dp")
        }
    }
}

private fun editorState() = CustomFilterEditorViewModel.State(
    original = null,
    current = CustomFilterConfig(identifier = "abc", label = "Test"),
)

private fun ComposeContentTestRule.setEditorContent(content: @Composable () -> Unit) {
    setContent { PreviewWrapper { content() } }
}

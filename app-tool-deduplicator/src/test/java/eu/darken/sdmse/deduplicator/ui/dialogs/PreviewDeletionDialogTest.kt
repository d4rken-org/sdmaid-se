package eu.darken.sdmse.deduplicator.ui.dialogs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class PreviewDeletionDialogTest : BaseComposeRobolectricTest() {

    private val toggleText = "Delete all copies. No file is left over!"

    private fun dupe(name: String): Duplicate = previewChecksumDuplicate(
        pathSegments = arrayOf("storage", "emulated", "0", "Pictures", name),
        size = 1024L,
        hashSeed = "seed",
    )

    private val a = dupe("a.jpg")
    private val b = dupe("b.jpg")
    private val c = dupe("c.jpg")

    // Preview tiles are the dialog's only clickable nodes without text (buttons carry labels) that
    // aren't the toggle.
    private val previewTile = hasClickAction() and
        SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
        SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState)

    private fun setDialog(mode: PreviewDeletionMode, onConfirm: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            PreviewWrapper {
                PreviewDeletionDialog(
                    mode = mode,
                    onConfirm = onConfirm,
                    onDismiss = {},
                    onPreviewClick = {},
                )
            }
        }
    }

    private fun folderModeWholeSetOfThree() = PreviewDeletionMode.Duplicates(
        duplicates = listOf(a, b),
        allowDeleteAll = true,
        deleteAllDuplicates = listOf(a, b, c),
    )

    @Test
    fun `duplicates without allowDeleteAll show no toggle`() {
        setDialog(PreviewDeletionMode.Duplicates(duplicates = listOf(a, b)))

        composeRule.onNodeWithText("Delete 2 selected items?").assertExists()
        composeRule.onAllNodesWithText(toggleText).assertCountEquals(0)
        composeRule.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun `whole-set folder starts with the toggle off and the reduced count`() {
        setDialog(folderModeWholeSetOfThree())

        composeRule.onNodeWithText(toggleText).assertExists()
        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNodeWithText("Delete 2 selected items?").assertExists()
    }

    @Test
    fun `whole-set folder message follows the toggle`() {
        setDialog(folderModeWholeSetOfThree())

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Delete 3 selected items?").assertExists()
        composeRule.onAllNodesWithText("Delete 2 selected items?").assertCountEquals(0)

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Delete 2 selected items?").assertExists()
        composeRule.onAllNodesWithText("Delete 3 selected items?").assertCountEquals(0)
    }

    @Test
    fun `Delete reports the toggle state`() {
        val confirmations = mutableListOf<Boolean>()
        setDialog(folderModeWholeSetOfThree(), onConfirm = { confirmations.add(it) })

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.runOnIdle { assertEquals(listOf(true, false), confirmations) }
    }

    @Test
    fun `two-file folder switches from the single-path message to the plural one`() {
        setDialog(
            PreviewDeletionMode.Duplicates(
                duplicates = listOf(a),
                allowDeleteAll = true,
                deleteAllDuplicates = listOf(a, b),
            ),
        )

        composeRule.onNodeWithText("Delete \"/storage/emulated/0/Pictures/a.jpg\"?").assertExists()

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Delete 2 selected items?").assertExists()
        composeRule.onAllNodesWithText("Delete \"/storage/emulated/0/Pictures/a.jpg\"?").assertCountEquals(0)
    }

    @Test
    fun `preview tiles follow the toggle`() {
        setDialog(folderModeWholeSetOfThree())

        composeRule.onAllNodes(previewTile).assertCountEquals(2)

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onAllNodes(previewTile).assertCountEquals(3)

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onAllNodes(previewTile).assertCountEquals(2)
    }

    @Test
    fun `two-file folder preview changes from the single image to a two-tile grid`() {
        setDialog(
            PreviewDeletionMode.Duplicates(
                duplicates = listOf(a),
                allowDeleteAll = true,
                deleteAllDuplicates = listOf(a, b),
            ),
        )

        composeRule.onAllNodes(previewTile).assertCountEquals(1)

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onAllNodes(previewTile).assertCountEquals(2)
    }
}

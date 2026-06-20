package eu.darken.sdmse.stats.ui.spacehistory

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SpaceHistoryScreenTest : BaseComposeRobolectricTest() {

    private val storageA = SpaceHistoryViewModel.StorageOption(
        id = "storage-a",
        label = "Storage Alpha".toCaString(),
    )
    private val storageB = SpaceHistoryViewModel.StorageOption(
        id = "storage-b",
        label = "Storage Beta".toCaString(),
    )

    private fun ComposeContentTestRule.setScreen(
        state: SpaceHistoryViewModel.State,
        onSelectStorage: (String) -> Unit = {},
        onDeleteStorage: (String) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                SpaceHistoryScreen(
                    stateSource = MutableStateFlow(state),
                    onSelectStorage = onSelectStorage,
                    onDeleteStorage = onDeleteStorage,
                )
            }
        }
    }

    @Test
    fun `tapping a storage chip selects it`() {
        val selected = mutableListOf<String>()
        composeRule.setScreen(
            state = SpaceHistoryViewModel.State(
                storages = listOf(storageA, storageB),
                selectedStorageId = storageA.id,
            ),
            onSelectStorage = { selected += it },
        )

        // The screen is vertically scrollable (the chart pushes the chips below the test viewport),
        // so bring the chip into view before driving the tap gesture.
        composeRule.onNodeWithText("Storage Beta").performScrollTo()
        composeRule.onNodeWithText("Storage Beta").performClick()
        composeRule.runOnIdle { assertEquals(listOf("storage-b"), selected) }
    }

    @Test
    fun `long-press on a storage chip opens the delete confirm dialog and confirming deletes`() {
        val deleted = mutableListOf<String>()
        composeRule.setScreen(
            state = SpaceHistoryViewModel.State(
                storages = listOf(storageA, storageB),
                selectedStorageId = storageA.id,
            ),
            onDeleteStorage = { deleted += it },
        )

        // The confirm dialog must not exist before the long-press.
        composeRule.onNodeWithText("Delete storage history?").assertDoesNotExist()

        composeRule.onNodeWithText("Storage Beta").performScrollTo()
        composeRule.onNodeWithText("Storage Beta").performTouchInput { longClick() }

        // Long-press is the only entry point to the delete-storage confirm dialog.
        composeRule.onNodeWithText("Delete storage history?").assertExists()

        // Confirm and assert the delete path fires with the long-pressed storage id.
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle { assertEquals(listOf("storage-b"), deleted) }
    }
}

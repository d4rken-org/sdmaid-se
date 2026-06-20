package eu.darken.sdmse.swiper.ui.status

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.preview.previewLocalPathLookup
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SwiperStatusScreenTest : BaseComposeRobolectricTest() {

    private fun item(id: Long, name: String): SwipeItem = previewSwipeItem(
        id = id,
        itemIndex = id.toInt(),
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "DCIM", name),
            size = 1024L * id,
        ),
        decision = SwipeDecision.UNDECIDED,
    )

    private fun ComposeContentTestRule.setStatusScreen(state: SwiperStatusViewModel.State) {
        setContent {
            PreviewWrapper {
                SwiperStatusScreen(stateSource = MutableStateFlow(state))
            }
        }
    }

    @Test
    fun `select-all action visible only with partial selection`() {
        composeRule.setStatusScreen(
            SwiperStatusViewModel.State(
                items = listOf(item(1L, "alpha"), item(2L, "beta")),
            ),
        )
        // Partial selection: long-press selects only the first row.
        composeRule.onNodeWithText("alpha").performTouchInput { longClick() }

        // Select-all icon button exposes its label as a content description (SdmSelectAllAction →
        // general_list_select_all_action).
        composeRule.onAllNodesWithContentDescription("Select all").fetchSemanticsNodes().size.let {
            if (it == 0) throw AssertionError("Expected Select all action visible with partial selection")
        }
    }

    @Test
    fun `select-all selects every item and then hides itself`() {
        composeRule.setStatusScreen(
            SwiperStatusViewModel.State(
                items = listOf(item(1L, "alpha"), item(2L, "beta"), item(3L, "gamma")),
            ),
        )

        // Enter selection mode with one item selected.
        composeRule.onNodeWithText("alpha").performTouchInput { longClick() }
        // Count from SdmSelectionTopAppBar (general_x_selected_count: "%d selected").
        composeRule.onNodeWithText("1 selected").assertExists()

        composeRule.onNodeWithContentDescription("Select all").performClick()

        // All three ids are now selected, so the count reflects the full set and the select-all
        // action disappears (visible = selection.size < itemIds.size).
        composeRule.onNodeWithText("3 selected").assertExists()
        composeRule.onAllNodesWithContentDescription("Select all").assertCountEquals(0)
    }
}

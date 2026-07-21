package eu.darken.sdmse.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SdmFastScrollerTest : BaseComposeRobolectricTest() {

    private val cd = "Fast scroller"

    @Composable
    private fun listFixture(itemCount: Int) {
        val state = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items((0 until itemCount).toList()) { index ->
                    Text(text = "Item $index", modifier = Modifier.height(8.dp))
                }
            }
            SdmFastScroller(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }

    @Test
    fun `hidden below default minItemsToShow threshold`() {
        composeRule.setContent {
            PreviewWrapper { listFixture(itemCount = 10) }
        }
        composeRule.onAllNodesWithContentDescription(cd).assertCountEquals(0)
    }

    @Test
    fun `shown above default minItemsToShow threshold for a long list`() {
        composeRule.setContent {
            // Items are tiny (8dp) so the viewport can hold many, but with 500 items the lane
            // still has plenty of items below the viewport — viewportItems < totalItems.
            PreviewWrapper { listFixture(itemCount = 500) }
        }
        composeRule.onNodeWithContentDescription(cd).assertIsDisplayed()
    }

    @Test
    fun `hidden when viewport holds every item even if count exceeds threshold`() {
        composeRule.setContent {
            PreviewWrapper {
                val state = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        // 30 items, each 1dp tall — all visible at once on any test surface.
                        items((0 until 30).toList()) { index ->
                            Text(text = "$index", modifier = Modifier.height(1.dp))
                        }
                    }
                    SdmFastScroller(
                        state = state,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
        composeRule.onAllNodesWithContentDescription(cd).assertCountEquals(0)
    }

    @Test
    fun `dragging lane to bottom moves list to its last portion`() {
        lateinit var state: LazyListState
        val itemCount = 500
        composeRule.setContent {
            PreviewWrapper {
                state = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        items((0 until itemCount).toList()) { index ->
                            Text(text = "Item $index", modifier = Modifier.height(8.dp))
                        }
                    }
                    SdmFastScroller(
                        state = state,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(cd).performTouchInput {
            swipeDown(durationMillis = 500)
        }

        composeRule.runOnIdle {
            if (state.firstVisibleItemIndex <= itemCount / 2) {
                throw AssertionError(
                    "Expected fast-scroll drag to reach the last portion, " +
                        "but first visible index was ${state.firstVisibleItemIndex}",
                )
            }
        }
    }

    @Test
    fun `dragging grid lane to bottom moves grid to its last portion`() {
        lateinit var state: LazyGridState
        val itemCount = 500
        composeRule.setContent {
            PreviewWrapper {
                state = rememberLazyGridState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        gridItems((0 until itemCount).toList()) { index ->
                            Text(text = "Item $index", modifier = Modifier.height(8.dp))
                        }
                    }
                    SdmFastScroller(
                        state = state,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(cd).performTouchInput {
            swipeDown(durationMillis = 500)
        }

        composeRule.runOnIdle {
            if (state.firstVisibleItemIndex <= itemCount / 2) {
                throw AssertionError(
                    "Expected fast-scroll drag to reach the grid's last portion, " +
                        "but first visible index was ${state.firstVisibleItemIndex}",
                )
            }
        }
    }
}

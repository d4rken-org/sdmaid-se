package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class ScrollAwareFabTest : BaseComposeRobolectricTest() {

    @Composable
    private fun TestContent(
        listState: LazyListState,
        itemCount: Int = 30,
        visible: Boolean = true,
        scrollHideEnabled: Boolean = true,
    ) {
        PreviewWrapper {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .testTag("list")
                        .fillMaxSize(),
                ) {
                    items((0 until itemCount).toList()) { i ->
                        Text(text = "Item $i", modifier = Modifier.height(80.dp))
                    }
                }
                ScrollAwareFab(
                    scrollState = listState,
                    visible = visible,
                    scrollHideEnabled = scrollHideEnabled,
                ) {
                    Box(modifier = Modifier.testTag("fab").size(56.dp))
                }
            }
        }
    }

    @Test
    fun `visible at the top of the list`() {
        val listState = LazyListState()
        composeRule.setContent { TestContent(listState) }

        composeRule.onNodeWithTag("fab").assertExists()
    }

    @Test
    fun `hides after scrolling down and reappears after scrolling up`() {
        val listState = LazyListState()
        composeRule.setContent { TestContent(listState) }

        composeRule.onNodeWithTag("list").performScrollToIndex(25)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fab").assertDoesNotExist()

        composeRule.onNodeWithTag("list").performScrollToIndex(0)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fab").assertExists()
    }

    @Test
    fun `visible=false hides the fab regardless of scroll`() {
        val listState = LazyListState()
        composeRule.setContent { TestContent(listState, visible = false) }

        composeRule.onNodeWithTag("fab").assertDoesNotExist()
    }

    @Test
    fun `scrollHideEnabled=false keeps the fab visible while scrolled down`() {
        val listState = LazyListState()
        composeRule.setContent { TestContent(listState, scrollHideEnabled = false) }

        composeRule.onNodeWithTag("list").performScrollToIndex(25)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fab").assertExists()
    }

    @Test
    fun `fab reappears when the list empties after being scrolled down`() {
        // Regression for the detached-list path: a scrolled-down list keeps reporting a stale
        // "scrolled down" signal after its content is swapped out. Hosts gate that off via
        // scrollHideEnabled (e.g. rows.isNotEmpty()), so the FAB must come back.
        val listState = LazyListState()
        val count = mutableStateOf(30)
        composeRule.setContent {
            TestContent(listState, itemCount = count.value, scrollHideEnabled = count.value > 0)
        }

        composeRule.onNodeWithTag("list").performScrollToIndex(25)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fab").assertDoesNotExist()

        composeRule.runOnIdle { count.value = 0 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fab").assertExists()
    }
}

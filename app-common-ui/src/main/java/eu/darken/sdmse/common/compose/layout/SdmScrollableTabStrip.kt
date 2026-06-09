package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Horizontally scrollable tab strip backed by a [LazyRow].
 *
 * Material's `ScrollableTabRow` subcomposes and measures every tab in a single pass, summing their
 * widths. With a large number of tabs that sum overflows Compose's `Constraints` and crashes with
 * `Can't represent a width of …`. This strip composes tabs lazily, so the tab count is effectively
 * unbounded.
 */
@Composable
fun SdmScrollableTabStrip(
    selectedTabIndex: Int,
    tabCount: Int,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit,
    tabContent: @Composable (index: Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Keep the selected tab on screen as the selection changes (e.g. via pager swipe), but skip the
    // scroll when it's already fully visible so visible tabs don't get yanked to the edge.
    LaunchedEffect(selectedTabIndex, tabCount) {
        if (selectedTabIndex !in 0 until tabCount) return@LaunchedEffect
        val info = listState.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == selectedTabIndex }
        val fullyVisible = target != null &&
            target.offset >= info.viewportStartOffset &&
            target.offset + target.size <= info.viewportEndOffset
        if (!fullyVisible) {
            listState.animateScrollToItem(selectedTabIndex)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            items(tabCount) { index ->
                SdmTab(
                    selected = index == selectedTabIndex,
                    onClick = { onTabSelected(index) },
                    content = { tabContent(index) },
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SdmTab(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 90.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                    content()
                }
            }
        }
        Box(
            modifier = Modifier
                .height(3.dp)
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Preview2
@Composable
private fun SdmScrollableTabStripPreview() {
    PreviewWrapper {
        SdmScrollableTabStrip(
            selectedTabIndex = 1,
            tabCount = 8,
            onTabSelected = {},
        ) { index ->
            Text(text = "Cluster #${index + 1}")
        }
    }
}

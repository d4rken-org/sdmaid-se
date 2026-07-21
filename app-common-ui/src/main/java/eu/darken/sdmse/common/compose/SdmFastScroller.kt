package eu.darken.sdmse.common.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlin.math.roundToInt

val SdmFastScrollerLaneWidth = 32.dp
internal val ThumbWidth = 8.dp
internal val ThumbHeight = 48.dp
internal val BubbleEndPadding = 8.dp
const val SdmFastScrollerDefaultMinItems = 30

@Composable
fun SdmFastScroller(
    state: LazyListState,
    modifier: Modifier = Modifier,
    sections: List<FastScrollSection> = emptyList(),
    minItemsToShow: Int = SdmFastScrollerDefaultMinItems,
) {
    val adapter = remember(state) { state.asFastScrollAdapter() }
    SdmFastScrollerImpl(
        adapter = adapter,
        modifier = modifier,
        sections = sections,
        minItemsToShow = minItemsToShow,
    )
}

@Composable
fun SdmFastScroller(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    sections: List<FastScrollSection> = emptyList(),
    minItemsToShow: Int = SdmFastScrollerDefaultMinItems,
) {
    val adapter = remember(state) { state.asFastScrollAdapter() }
    SdmFastScrollerImpl(
        adapter = adapter,
        modifier = modifier,
        sections = sections,
        minItemsToShow = minItemsToShow,
    )
}

data class FastScrollSection(val itemIndex: Int, val label: String)

internal interface FastScrollAdapter {
    val totalItems: Int
    val viewportItems: Int
    val firstVisibleIndex: Int
    fun requestScrollToItem(index: Int)
}

internal fun LazyListState.asFastScrollAdapter(): FastScrollAdapter = object : FastScrollAdapter {
    override val totalItems: Int get() = layoutInfo.totalItemsCount
    override val viewportItems: Int get() = layoutInfo.visibleItemsInfo.size
    override val firstVisibleIndex: Int get() = firstVisibleItemIndex
    override fun requestScrollToItem(index: Int) = this@asFastScrollAdapter.requestScrollToItem(index)
}

internal fun LazyGridState.asFastScrollAdapter(): FastScrollAdapter = object : FastScrollAdapter {
    override val totalItems: Int get() = layoutInfo.totalItemsCount
    override val viewportItems: Int get() = layoutInfo.visibleItemsInfo.size
    override val firstVisibleIndex: Int get() = firstVisibleItemIndex
    override fun requestScrollToItem(index: Int) = this@asFastScrollAdapter.requestScrollToItem(index)
}

@Composable
private fun SdmFastScrollerImpl(
    adapter: FastScrollAdapter,
    modifier: Modifier = Modifier,
    sections: List<FastScrollSection> = emptyList(),
    minItemsToShow: Int = SdmFastScrollerDefaultMinItems,
) {
    val totalItems = adapter.totalItems
    val viewportItems = adapter.viewportItems

    // Hide entirely when there's nothing to fast-scroll through.
    if (totalItems < minItemsToShow) return
    if (viewportItems == 0) return
    if (viewportItems >= totalItems) return

    val density = LocalDensity.current

    var trackHeightPx by remember { mutableIntStateOf(0) }
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var dragTargetIndex by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val scrollableRange = (totalItems - viewportItems).coerceAtLeast(1)
    val displayFraction = if (dragging) {
        dragFraction
    } else {
        (adapter.firstVisibleIndex.toFloat() / scrollableRange).coerceIn(0f, 1f)
    }

    val activeSection = if (!dragging || sections.isEmpty()) {
        null
    } else {
        sections.lastOrNull { it.itemIndex <= dragTargetIndex } ?: sections.first()
    }

    val fastScrollerCd = stringResource(CommonR.string.general_fast_scroller_action)
    val statePercent = "${(displayFraction * 100).roundToInt()}%"

    Box(
        modifier = modifier
            .width(SdmFastScrollerLaneWidth)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(adapter, totalItems) {
                var lastRequestedIndex = -1

                fun updateDragPosition(y: Float) {
                    val count = adapter.totalItems
                    if (count <= 0) return

                    val fraction = (y / size.height.toFloat()).coerceIn(0f, 1f)
                    val target = (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
                    dragFraction = fraction
                    dragTargetIndex = target

                    if (target != lastRequestedIndex) {
                        lastRequestedIndex = target
                        // Unlike scrollToItem(), this schedules a remeasure instead of forcing one
                        // synchronously. Rapid pointer updates therefore collapse to the newest
                        // requested position before the next frame is measured.
                        adapter.requestScrollToItem(target)
                    }
                }

                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        // Always submit the first position of a new gesture, even when it matches
                        // the previous gesture's last target and the list moved in between.
                        lastRequestedIndex = -1
                        updateDragPosition(offset.y)
                    },
                    onVerticalDrag = { change, _ ->
                        updateDragPosition(change.position.y)
                        change.consume()
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                )
            }
            .semantics {
                contentDescription = fastScrollerCd
                stateDescription = statePercent
            },
    ) {
        val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbY = (travelPx * displayFraction).roundToInt()

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbY) }
                .size(width = ThumbWidth, height = ThumbHeight)
                .clip(RoundedCornerShape(ThumbWidth / 2))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )

        // Bubble: aligned to the END of the lane so its right edge sits at the lane's right
        // edge by default, then offset leftward by lane width + a small gap so the entire
        // bubble floats outside the lane (in the list's content area). Alignment.TopEnd flips
        // to start in RTL, and the offset's sign would then point the wrong way, so guard with
        // a layout direction check.
        val bubbleOffsetPx = with(density) { (SdmFastScrollerLaneWidth + BubbleEndPadding).roundToPx() }
        // In RTL, Alignment.TopEnd resolves to the left edge, so the bubble must offset rightward
        // (positive x) to float into the content area instead of further off-screen.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        AnimatedVisibility(
            visible = activeSection != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = if (isRtl) bubbleOffsetPx else -bubbleOffsetPx, y = thumbY) },
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = activeSection?.label ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SdmFastScrollerListPreview() {
    PreviewWrapper {
        val state = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(200) { index ->
                    Box(modifier = Modifier.height(56.dp).padding(16.dp)) {
                        Text("Item $index")
                    }
                }
            }
            SdmFastScroller(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Preview2
@Composable
private fun SdmFastScrollerGridWithSectionsPreview() {
    PreviewWrapper {
        val state = rememberLazyGridState()
        val sections = remember {
            ('A'..'Z').mapIndexed { i, c -> FastScrollSection(itemIndex = i * 8, label = c.toString()) }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = state,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(208) { index ->
                    Box(modifier = Modifier.height(56.dp).padding(16.dp)) {
                        Text("Item $index")
                    }
                }
            }
            SdmFastScroller(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                sections = sections,
            )
        }
    }
}

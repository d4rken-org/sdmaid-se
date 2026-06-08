package eu.darken.sdmse.common.debug.logviewer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.OpenInFull
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logviewer.core.LogLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt
import eu.darken.sdmse.common.R as CommonR

@Composable
internal fun FloatingLogPanel(
    modifier: Modifier = Modifier,
    stateSource: Flow<FloatingLogPanelViewModel.State> = flowOf(FloatingLogPanelViewModel.State()),
    onSetQuery: (String) -> Unit = {},
    onNextMatch: () -> Unit = {},
    onPrevMatch: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onSetLevel: (Logging.Priority) -> Unit = {},
    onClear: () -> Unit = {},
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = FloatingLogPanelViewModel.State())
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    // Inset into the safe-drawing area: the activity is edge-to-edge, so without this the header
    // (drag handle) would sit under the status bar where the system steals the drag gesture.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val density = LocalDensity.current
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }

        val bubblePx = with(density) { BUBBLE_SIZE.toPx() }
        val minWpx = with(density) { MIN_WIDTH.toPx() }
        val minHpx = with(density) { MIN_HEIGHT.toPx() }
        // Modest default so the panel starts out of the way; the user resizes up from here.
        val defaultWpx = with(density) { minOf(maxWidth - 24.dp, DEFAULT_WIDTH).toPx() }
        val defaultHpx = with(density) { (maxHeight * 0.4f).coerceIn(MIN_HEIGHT, DEFAULT_MAX_HEIGHT).toPx() }

        // Resizable panel size (px). NaN until first laid out / first resize.
        var widthState by rememberSaveable { mutableStateOf(Float.NaN) }
        var heightState by rememberSaveable { mutableStateOf(Float.NaN) }

        val effWpx = (if (widthState.isNaN()) defaultWpx else widthState).coerceIn(minWpx, containerW)
        val effHpx = (if (heightState.isNaN()) defaultHpx else heightState).coerceIn(minHpx, containerH)

        val activeW = if (collapsed) bubblePx else effWpx
        val activeH = if (collapsed) bubblePx else effHpx

        // Drag position relative to the top-start corner. Re-clamped whenever the container or the
        // active size changes (rotation / window resize / collapse / resize).
        var offsetX by rememberSaveable { mutableStateOf(Float.NaN) }
        var offsetY by rememberSaveable { mutableStateOf(Float.NaN) }

        LaunchedEffect(containerW, containerH, activeW, activeH) {
            val maxX = (containerW - activeW).coerceAtLeast(0f)
            val maxY = (containerH - activeH).coerceAtLeast(0f)
            if (offsetX.isNaN() || offsetY.isNaN()) {
                offsetX = maxX
                offsetY = with(density) { 24.dp.toPx() }.coerceAtMost(maxY)
            }
            offsetX = offsetX.coerceIn(0f, maxX)
            offsetY = offsetY.coerceIn(0f, maxY)
        }

        // Move: only from the title/header.
        val dragModifier = Modifier.pointerInput(containerW, containerH, collapsed) {
            detectDragGestures { change, drag ->
                change.consume()
                val maxX = (containerW - activeW).coerceAtLeast(0f)
                val maxY = (containerH - activeH).coerceAtLeast(0f)
                offsetX = (offsetX + drag.x).coerceIn(0f, maxX)
                offsetY = (offsetY + drag.y).coerceIn(0f, maxY)
            }
        }

        // Resize from the bottom-end grip: right & bottom edges follow the pointer, top-left pinned.
        val resizeRightModifier = Modifier.pointerInput(containerW, containerH) {
            detectDragGestures { change, drag ->
                change.consume()
                val curW = if (widthState.isNaN()) defaultWpx else widthState
                val curH = if (heightState.isNaN()) defaultHpx else heightState
                val ox = if (offsetX.isNaN()) 0f else offsetX
                val oy = if (offsetY.isNaN()) 0f else offsetY
                widthState = (curW + drag.x).coerceIn(minWpx, (containerW - ox).coerceAtLeast(minWpx))
                heightState = (curH + drag.y).coerceIn(minHpx, (containerH - oy).coerceAtLeast(minHpx))
            }
        }

        // Resize from the bottom-start grip: left edge follows the pointer (right edge pinned),
        // bottom edge follows as usual.
        val resizeLeftModifier = Modifier.pointerInput(containerW, containerH) {
            detectDragGestures { change, drag ->
                change.consume()
                val curW = if (widthState.isNaN()) defaultWpx else widthState
                val curH = if (heightState.isNaN()) defaultHpx else heightState
                val ox = if (offsetX.isNaN()) 0f else offsetX
                val oy = if (offsetY.isNaN()) 0f else offsetY
                // Clamp the horizontal delta so the left edge stays on-screen and width >= min.
                val effDx = drag.x.coerceIn(-ox, curW - minWpx)
                offsetX = ox + effDx
                widthState = curW - effDx
                heightState = (curH + drag.y).coerceIn(minHpx, (containerH - oy).coerceAtLeast(minHpx))
            }
        }

        val positionModifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                IntOffset(
                    x = if (offsetX.isNaN()) 0 else offsetX.roundToInt(),
                    y = if (offsetY.isNaN()) 0 else offsetY.roundToInt(),
                )
            }

        if (collapsed) {
            CollapsedBubble(
                modifier = positionModifier,
                dragModifier = dragModifier,
                size = BUBBLE_SIZE,
                hasErrors = state.lines.any { it.priority >= Logging.Priority.ERROR },
                onExpand = { collapsed = false },
            )
        } else {
            ExpandedPanel(
                modifier = positionModifier,
                dragModifier = dragModifier,
                resizeLeftModifier = resizeLeftModifier,
                resizeRightModifier = resizeRightModifier,
                width = with(density) { effWpx.toDp() },
                height = with(density) { effHpx.toDp() },
                state = state,
                searchVisible = searchVisible,
                onToggleSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) onSetQuery("")
                },
                onSetQuery = onSetQuery,
                onNextMatch = onNextMatch,
                onPrevMatch = onPrevMatch,
                onTogglePause = onTogglePause,
                onSetLevel = onSetLevel,
                onClear = onClear,
                onCopy = onCopy,
                onShare = onShare,
                onCollapse = { collapsed = true },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun CollapsedBubble(
    modifier: Modifier,
    dragModifier: Modifier,
    size: Dp,
    hasErrors: Boolean,
    onExpand: () -> Unit,
) {
    Surface(
        modifier = modifier.size(size).then(dragModifier),
        shape = CircleShape,
        color = if (hasErrors) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        onClick = onExpand,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.Article,
                contentDescription = stringResource(R.string.debug_logview_screen_title),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ExpandedPanel(
    modifier: Modifier,
    dragModifier: Modifier,
    resizeLeftModifier: Modifier,
    resizeRightModifier: Modifier,
    width: Dp,
    height: Dp,
    state: FloatingLogPanelViewModel.State,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit,
    onSetQuery: (String) -> Unit,
    onNextMatch: () -> Unit,
    onPrevMatch: () -> Unit,
    onTogglePause: () -> Unit,
    onSetLevel: (Logging.Priority) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
) {
    var showLevelDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.width(width).height(height),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header doubles as the (only) drag handle.
                Row(
                    modifier = Modifier.fillMaxWidth().then(dragModifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.Article,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 12.dp).size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.debug_logview_screen_title),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.TwoTone.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.debug_logview_collapse_action),
                        )
                    }
                    OverflowMenu(
                        state = state,
                        onToggleSearch = onToggleSearch,
                        onTogglePause = onTogglePause,
                        onOpenLevelDialog = { showLevelDialog = true },
                        onClear = onClear,
                        onCopy = onCopy,
                        onShare = onShare,
                        onClose = onClose,
                    )
                }

                // Search row is hidden by default; toggled from the overflow menu.
                if (searchVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactSearchField(
                            query = state.query,
                            onQueryChange = onSetQuery,
                            onClose = onToggleSearch,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.query.isNotBlank()) {
                            Text(
                                text = "${state.currentOrdinal}/${state.matchCount}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            IconButton(
                                onClick = onPrevMatch,
                                enabled = state.matchCount > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.debug_logview_search_prev_action),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = onNextMatch,
                                enabled = state.matchCount > 0,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.debug_logview_search_next_action),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                if (state.isPaused) {
                    Text(
                        text = if (state.pausedDropCount > 0) {
                            stringResource(R.string.debug_logview_paused_dropped_msg, state.pausedDropCount)
                        } else {
                            stringResource(R.string.debug_logview_paused_msg)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                LogPanelList(
                    modifier = Modifier.fillMaxSize(),
                    lines = state.lines,
                    query = state.query,
                    currentMatchLineId = state.currentMatchLineId,
                )
            }

            // Resize grips in both bottom corners; the jump-to-bottom FAB sits bottom-center.
            ResizeGrip(
                modifier = Modifier.align(Alignment.BottomStart).then(resizeLeftModifier),
            )
            ResizeGrip(
                modifier = Modifier.align(Alignment.BottomEnd).then(resizeRightModifier),
            )
        }
    }

    if (showLevelDialog) {
        LogLevelDialog(
            current = state.minPriority,
            onSelect = onSetLevel,
            onDismiss = { showLevelDialog = false },
        )
    }
}

@Composable
private fun OverflowMenu(
    state: FloatingLogPanelViewModel.State,
    onToggleSearch: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenLevelDialog: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.TwoTone.MoreVert,
                contentDescription = stringResource(R.string.debug_logview_more_action),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(CommonR.string.general_search_action)) },
                leadingIcon = { Icon(Icons.TwoTone.Search, contentDescription = null) },
                onClick = {
                    expanded = false
                    onToggleSearch()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (state.isPaused) R.string.debug_logview_resume_action else R.string.debug_logview_pause_action
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (state.isPaused) Icons.TwoTone.PlayArrow else Icons.TwoTone.Pause,
                        contentDescription = null,
                        tint = if (state.isPaused) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                },
                onClick = {
                    expanded = false
                    onTogglePause()
                },
            )
            // Level selection opens a dialog so the menu doesn't grow with every priority.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_logview_level_action)) },
                leadingIcon = { Icon(Icons.TwoTone.Tune, contentDescription = null) },
                trailingIcon = {
                    Text(
                        text = state.minPriority.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                onClick = {
                    expanded = false
                    onOpenLevelDialog()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_logview_clear_action)) },
                leadingIcon = { Icon(Icons.TwoTone.DeleteSweep, contentDescription = null) },
                onClick = {
                    expanded = false
                    onClear()
                },
            )

            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_logview_copy_action)) },
                leadingIcon = { Icon(Icons.TwoTone.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CommonR.string.general_share_action)) },
                leadingIcon = { Icon(Icons.TwoTone.Share, contentDescription = null) },
                onClick = {
                    expanded = false
                    onShare()
                },
            )

            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_logview_close_action)) },
                leadingIcon = { Icon(Icons.TwoTone.Close, contentDescription = null) },
                onClick = {
                    expanded = false
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun LogLevelDialog(
    current: Logging.Priority,
    onSelect: (Logging.Priority) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_logview_level_action)) },
        text = {
            Column {
                LEVELS.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(level)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = level == current,
                            onClick = {
                                onSelect(level)
                                onDismiss()
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(level.displayName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_close_action))
            }
        },
    )
}

@Composable
private fun ResizeGrip(modifier: Modifier) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.TwoTone.OpenInFull,
            contentDescription = stringResource(R.string.debug_logview_resize_action),
            modifier = Modifier.size(14.dp),
            tint = LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(CommonR.string.general_search_action),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Icon(
                imageVector = Icons.TwoTone.Close,
                contentDescription = stringResource(CommonR.string.general_close_action),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Logging.Priority.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private val LEVELS = listOf(
    Logging.Priority.VERBOSE,
    Logging.Priority.DEBUG,
    Logging.Priority.INFO,
    Logging.Priority.WARN,
    Logging.Priority.ERROR,
)

private val BUBBLE_SIZE = 48.dp
private val MIN_WIDTH = 200.dp
private val MIN_HEIGHT = 140.dp
private val DEFAULT_WIDTH = 320.dp
private val DEFAULT_MAX_HEIGHT = 280.dp

@Preview2
@Composable
private fun FloatingLogPanelPreview() {
    PreviewWrapper {
        FloatingLogPanel(
            stateSource = flowOf(
                FloatingLogPanelViewModel.State(
                    lines = listOf(
                        LogLine(1, Logging.Priority.DEBUG, "AppCleaner", "Scanning /data/data"),
                        LogLine(2, Logging.Priority.INFO, "CorpseFinder", "Found 3 corpses"),
                        LogLine(3, Logging.Priority.WARN, "GatewaySwitch", "Skipped Android/obb"),
                        LogLine(4, Logging.Priority.ERROR, "RootService", "Connection failed"),
                    ),
                    query = "found",
                    matchCount = 1,
                    currentOrdinal = 1,
                    currentMatchLineId = 2,
                )
            ),
        )
    }
}

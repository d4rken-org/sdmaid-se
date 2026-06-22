package eu.darken.sdmse.common.debug.logviewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logviewer.core.LogLine
import kotlinx.coroutines.launch

@Composable
internal fun LogPanelList(
    modifier: Modifier = Modifier,
    lines: List<LogLine>,
    query: String,
    currentMatchLineId: Long?,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Explicit follow-the-tail lock. Layout-derived "at bottom" is unreliable here: stable ids
    // re-anchor the viewport the instant new lines arrive, so it reads "not at bottom" exactly when
    // we'd want to follow. Instead we follow until the user scrolls, and re-arm via the button.
    var followMode by rememberSaveable { mutableStateOf(true) }

    // Any user-initiated scroll drops follow. Programmatic scrollToItem is instant and never sets
    // isScrollInProgress, so it won't trip this.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress -> if (inProgress) followMode = false }
    }

    val lastId = lines.lastOrNull()?.id
    LaunchedEffect(lastId, followMode, currentMatchLineId) {
        if (followMode && currentMatchLineId == null && lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    // Park on the active search match (search takes precedence over follow).
    LaunchedEffect(currentMatchLineId) {
        if (currentMatchLineId != null) {
            val idx = lines.indexOfFirst { it.id == currentMatchLineId }
            if (idx >= 0) listState.scrollToItem(idx)
        }
    }

    Box(modifier = modifier) {
        val highlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            state = listState,
        ) {
            items(lines, key = { it.id }) { line ->
                LogRow(line = line, query = query, highlightColor = highlightColor)
            }
        }
        if (!followMode && lines.isNotEmpty()) {
            SmallFloatingActionButton(
                onClick = {
                    followMode = true
                    scope.launch { listState.scrollToItem(lines.lastIndex) }
                },
                // Bottom-center so it clears the bottom corners (move grip / resize grip).
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.VerticalAlignBottom,
                    contentDescription = stringResource(R.string.debug_logview_jump_to_bottom_action),
                )
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine, query: String, highlightColor: Color) {
    val color = logPriorityColor(line.priority)
    val rendered = buildAnnotatedString {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
            append(line.priority.shortLabel)
            append(' ')
        }
        withStyle(SpanStyle(color = color.copy(alpha = 0.7f))) {
            append(line.tag)
            append(": ")
        }
        appendHighlighted(line.message, query, highlightColor)
    }
    Text(
        text = rendered,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        ),
    )
}

/** Appends [text], wrapping each case-insensitive occurrence of [query] in a [background] span. */
private fun AnnotatedString.Builder.appendHighlighted(text: String, query: String, background: Color) {
    if (query.isBlank()) {
        append(text)
        return
    }
    // Match on the original text (case-insensitive) so Unicode case-folding length changes can't
    // misalign spans or overrun the string.
    val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
    var start = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > start) append(text.substring(start, match.range.first))
        withStyle(SpanStyle(background = background)) {
            append(text.substring(match.range.first, match.range.last + 1))
        }
        start = match.range.last + 1
    }
    if (start < text.length) append(text.substring(start))
}

@Composable
private fun logPriorityColor(priority: Logging.Priority): Color = when (priority) {
    Logging.Priority.ERROR, Logging.Priority.ASSERT -> MaterialTheme.colorScheme.error
    Logging.Priority.WARN -> MaterialTheme.colorScheme.tertiary
    Logging.Priority.INFO -> MaterialTheme.colorScheme.primary
    else -> LocalContentColor.current.copy(alpha = 0.8f)
}

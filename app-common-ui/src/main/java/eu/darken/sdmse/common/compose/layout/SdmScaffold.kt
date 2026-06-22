package eu.darken.sdmse.common.compose.layout

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import eu.darken.sdmse.common.compose.focus.LocalDpadFocusMemory
import eu.darken.sdmse.common.compose.focus.rememberDpadFocusMemory
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Drop-in [Scaffold] replacement that makes screens D-pad-navigable.
 *
 * The problem it solves: screen bodies are full-window scrollable containers (lazy lists,
 * scroll columns) — focus groups whose rect fully CONTAINS the chrome controls' rects
 * (top bar icons, FAB, snackbar action). Compose's directional focus search never considers
 * a focus group that contains the focused node as a candidate, so its children are never
 * searched: DOWN from the top bar's back arrow finds nothing and focus is stuck in the
 * chrome ("focus pocket" — same geometry the dashboard chrome fix documented).
 *
 * The fix is key-event bridges sending each chrome slot's D-pad moves into the content group:
 * - top bar: DOWN enters the content
 * - bottom bar, FAB, snackbar: UP enters the content (and LEFT from the FAB, which sits at
 *   the screen edge)
 *
 * The bridges are implemented via [onKeyEvent] + [FocusRequester.requestFocus] instead of
 * `focusProperties { down = ... }` custom destinations: a custom destination that fails to
 * take focus (content group with no focusable children — loading/empty screens) swallows the
 * key without falling back to geometric search, leaving chrome slots unable to reach each
 * other (top bar could never reach the FAB on an empty screen). The key-event bridge returns
 * the request's success, so an unconsumed key falls through to the default geometric search,
 * which finds the other chrome slots naturally.
 *
 * The reverse directions need no plumbing — chrome rects don't contain content-row rects,
 * so normal geometric search finds them.
 *
 * The content slot is wrapped in an always-composed focus group carrying the shared
 * [FocusRequester]; this keeps the bridge valid during loading/empty states and
 * [focusRestorer] returns focus to the last-focused row when re-entering via a bridge.
 *
 * Touch UX is unaffected: the bridges only react to D-pad / keyboard arrow key events.
 * Not gated on TV detection so external keyboards on phones benefit too.
 *
 * The dashboard keeps its bespoke grid/chrome wiring; sheet-based screens
 * (BottomSheetScaffold) are not covered by this component.
 */
@Composable
fun SdmScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val contentFocus = remember { FocusRequester() }
    // Saveable D-pad focus memory for the content slot: [focusRestorer] below only covers
    // within-lifetime re-entry from chrome; this survives NavEntry disposal so the remembered
    // row regains focus when returning from a pushed sub-screen (see DpadFocusMemory).
    val focusMemory = rememberDpadFocusMemory()

    Scaffold(
        modifier = modifier,
        topBar = {
            Box(
                Modifier.bridgeIntoContent(contentFocus, listOf(FocusDirection.Down)),
            ) { topBar() }
        },
        bottomBar = {
            Box(
                Modifier.bridgeIntoContent(contentFocus, listOf(FocusDirection.Up)),
            ) { bottomBar() }
        },
        snackbarHost = {
            Box(
                Modifier.bridgeIntoContent(contentFocus, listOf(FocusDirection.Up)),
            ) { snackbarHost() }
        },
        floatingActionButton = {
            Box(
                Modifier.bridgeIntoContent(contentFocus, listOf(FocusDirection.Up, FocusDirection.Left)),
            ) { floatingActionButton() }
        },
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { paddingValues ->
        Box(
            Modifier
                .focusRequester(contentFocus)
                .focusRestorer()
                .focusGroup(),
        ) {
            CompositionLocalProvider(LocalDpadFocusMemory provides focusMemory) {
                content(paddingValues)
            }
        }
    }
}

/**
 * Routes the given D-pad [directions] from this chrome slot into the content focus group.
 * Returns the focus request's success as the consumed flag: if the content group has no
 * focusable children, the key stays unconsumed and default geometric focus search runs,
 * keeping the remaining chrome slots (FAB, bottom bar, top bar) reachable from each other.
 */
private fun Modifier.bridgeIntoContent(
    contentFocus: FocusRequester,
    directions: List<FocusDirection>,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val direction = when (event.key) {
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        else -> return@onKeyEvent false
    }
    if (direction !in directions) return@onKeyEvent false
    contentFocus.requestFocus(direction)
}

@Preview2
@Composable
private fun SdmScaffoldPreview() {
    PreviewWrapper {
        SdmScaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Title") },
                    navigationIcon = {
                        SdmTooltipIconButton(
                            icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                            label = "Back",
                            onClick = {},
                        )
                    },
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                items(3) { index ->
                    Text("Row $index")
                }
            }
        }
    }
}

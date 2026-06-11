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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
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
 * The fix is custom focus destinations bridging each chrome slot into the content group:
 * - top bar: DOWN enters the content
 * - bottom bar, FAB, snackbar: UP enters the content (and LEFT from the FAB, which sits at
 *   the screen edge)
 *
 * The reverse directions need no plumbing — chrome rects don't contain content-row rects,
 * so normal geometric search finds them.
 *
 * The content slot is wrapped in an always-composed focus group carrying the shared
 * [FocusRequester]; this keeps the bridge valid during loading/empty states (entering a
 * group with no focusable children is a graceful no-op) and [focusRestorer] returns focus
 * to the last-focused row when re-entering via a bridge.
 *
 * Touch UX is unaffected: custom destinations only participate in directional (D-pad /
 * keyboard) focus moves. Not gated on TV detection so external keyboards on phones benefit
 * too.
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

    Scaffold(
        modifier = modifier,
        // Plain focusProperties (no focusGroup) on the slot wrappers: the custom destination
        // must reach the slot's descendant focus targets, and focusGroup() inserts its own
        // focusProperties node (canFocus gate) between this one and the children, shadowing
        // the bridge — same reason the dashboard chrome applies its escape without a group.
        topBar = {
            Box(
                Modifier.focusProperties { down = contentFocus },
            ) { topBar() }
        },
        bottomBar = {
            Box(
                Modifier.focusProperties { up = contentFocus },
            ) { bottomBar() }
        },
        snackbarHost = {
            Box(
                Modifier.focusProperties { up = contentFocus },
            ) { snackbarHost() }
        },
        floatingActionButton = {
            Box(
                Modifier.focusProperties {
                    up = contentFocus
                    left = contentFocus
                },
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
        ) { content(paddingValues) }
    }
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

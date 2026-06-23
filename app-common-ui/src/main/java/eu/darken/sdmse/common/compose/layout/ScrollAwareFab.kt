package eu.darken.sdmse.common.compose.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Wraps a floating action button so it hides while scrolling down a list and reappears when
 * scrolling up or at the top.
 *
 * Material 3's [androidx.compose.material3.Scaffold] floats the FAB over the content and does NOT
 * reserve space for it, so the host list must also add [SdmListDefaults.FabClearance] to its bottom
 * content padding — otherwise the FAB covers the last item.
 *
 * The scroll signal uses [ScrollableState.lastScrolledBackward] / [ScrollableState.canScrollBackward]
 * so a single helper covers both [androidx.compose.foundation.lazy.LazyListState] and
 * [androidx.compose.foundation.lazy.grid.LazyGridState].
 *
 * @param visible An animated visibility gate ANDed with the scroll signal. Use it for gates that
 *   should themselves animate (e.g. a scan-in-progress state). Hard gates that must remove the FAB
 *   instantly and unconditionally (e.g. selection mode) belong OUTSIDE this composable as a plain
 *   `if`, so the FAB can't be tapped while animating out.
 * @param scrollHideEnabled When the host swaps its scrollable container out (empty / loading state),
 *   the remembered scroll state keeps reporting its last "scrolled down" value, which would wrongly
 *   keep the FAB hidden. Pass `false` in those branches (e.g. `rows.isNotEmpty()`) to force the FAB
 *   visible regardless of the stale scroll signal.
 */
@Composable
fun ScrollAwareFab(
    modifier: Modifier = Modifier,
    scrollState: ScrollableState,
    visible: Boolean = true,
    scrollHideEnabled: Boolean = true,
    fab: @Composable () -> Unit,
) {
    val scrollVisible by remember(scrollState) {
        derivedStateOf { !scrollState.canScrollBackward || scrollState.lastScrolledBackward }
    }
    AnimatedVisibility(
        visible = visible && (!scrollHideEnabled || scrollVisible),
        modifier = modifier,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        fab()
    }
}

@Preview2
@Composable
private fun ScrollAwareFabPreview() {
    PreviewWrapper {
        ScrollAwareFab(scrollState = rememberLazyListState()) {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.TwoTone.Add, contentDescription = null)
            }
        }
    }
}

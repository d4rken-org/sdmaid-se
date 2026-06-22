package eu.darken.sdmse.common.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Project-wide modal bottom sheet wrapper for *in-screen* sheets (those toggled by a local
 * `mutableState` flag, not by a navigation entry).
 *
 * For *route-based* sheets, attach [eu.darken.sdmse.common.navigation.modalBottomSheetMetadata]
 * to the entry instead — the [eu.darken.sdmse.common.navigation.ModalBottomSheetSceneStrategy]
 * keeps the previous screen composed underneath, so dismissing the sheet does not reveal an
 * empty background.
 *
 * Bundles [rememberModalBottomSheetState] with `skipPartiallyExpanded = true` and exposes an
 * animated `dismiss(then)` helper to [content]. Calling `dismiss { /* then */ }` runs the
 * sheet's hide animation and only invokes `then` once it has finished — useful when an action
 * inside the sheet would otherwise yank the sheet off-screen without animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdmModalBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.(dismiss: (then: () -> Unit) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismiss: (then: () -> Unit) -> Unit = remember(sheetState, onDismiss, scope) {
        { then ->
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    then()
                    onDismiss()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        content(dismiss)
    }
}

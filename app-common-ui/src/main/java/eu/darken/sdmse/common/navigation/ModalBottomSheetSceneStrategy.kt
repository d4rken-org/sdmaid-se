package eu.darken.sdmse.common.navigation

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Metadata key flagging a [NavEntry] as a modal bottom sheet. Recognised by
 * [ModalBottomSheetSceneStrategy].
 */
const val MODAL_BOTTOM_SHEET_METADATA_KEY = "sdm-modal-bottom-sheet"

/**
 * Convenience helper for [androidx.navigation3.runtime.EntryProviderScope.entry] — pass as the
 * `metadata` argument to mark a route as a modal bottom sheet:
 *
 * ```
 * entry<MySheetRoute>(metadata = modalBottomSheetMetadata()) { route -> MySheetHost(route) }
 * ```
 *
 * The strategy then renders the entry inside a [ModalBottomSheet] *on top of* the previous
 * back-stack entry, which stays composed underneath. Compared to a regular entry, this avoids
 * the empty-background flash that would otherwise appear while the previous screen recomposes
 * during the sheet's open/close transition.
 */
fun modalBottomSheetMetadata(): Map<String, Any> = mapOf(MODAL_BOTTOM_SHEET_METADATA_KEY to true)

/**
 * Nav3 [SceneStrategy] that turns entries marked with [modalBottomSheetMetadata] into modal
 * bottom sheets layered on top of the previous entry.
 *
 * Wire it into [androidx.navigation3.ui.NavDisplay] via
 * `sceneStrategy = remember { ModalBottomSheetSceneStrategy<NavKey>(isTv).then(SinglePaneSceneStrategy()) }`.
 *
 * Programmatic dismissal: when the host calls `vm.navUp()` (e.g. after a Save action), the
 * entry is popped from the back stack and the sheet disappears without animating. That matches
 * the project's pre-existing behaviour and is acceptable because the previous screen — which
 * the [OverlayScene] kept composed — is already on screen behind the sheet. The empty-flash
 * bug, where the previous screen needed to recompose during dismissal, is what this strategy
 * fixes.
 *
 * @param isTv on TV-like devices the sheet's drag/nested-scroll gestures are disabled, turning
 * it into a fixed panel. Without this, D-pad focus moving into a non-scrollable sheet list emits
 * a `bringIntoView` scroll that the sheet's nested-scroll connection consumes by sliding the whole
 * sheet up, pushing its footer off the bottom edge. There is no touchscreen on TV, so nothing is
 * lost; phones keep swipe-to-dismiss.
 */
class ModalBottomSheetSceneStrategy<T : Any>(
    private val isTv: Boolean = false,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val top = entries.lastOrNull() ?: return null
        if (top.metadata[MODAL_BOTTOM_SHEET_METADATA_KEY] != true) return null
        val previous = entries.dropLast(1)
        return ModalBottomSheetScene(
            key = top.contentKey,
            entry = top,
            previousEntries = previous,
            overlaidEntries = previous,
            onBack = onBack,
            gesturesEnabled = !isTv,
        )
    }
}

private class ModalBottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
    private val gesturesEnabled: Boolean,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    @OptIn(ExperimentalMaterial3Api::class)
    override val content: @Composable () -> Unit = {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // With gestures off (TV) the handle is a dead focus stop that also implies a drag the
        // sheet no longer accepts — drop it so the sheet reads as a fixed panel. BACK dismisses.
        val dragHandle: (@Composable () -> Unit)? = if (gesturesEnabled) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        }
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = sheetState,
            sheetGesturesEnabled = gesturesEnabled,
            dragHandle = dragHandle,
        ) {
            entry.Content()
        }
    }

    // Nav3 expects Scene instances to be value-equal when they represent the same situation —
    // see the bundled DialogScene. Exclude [onBack] and [content] since they're fresh lambdas
    // every recalculation; that would otherwise churn overlay bookkeeping for predictive-back.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModalBottomSheetScene<*>) return false
        return key == other.key &&
            entry == other.entry &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            gesturesEnabled == other.gesturesEnabled
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + entry.hashCode()
        result = 31 * result + previousEntries.hashCode()
        result = 31 * result + overlaidEntries.hashCode()
        result = 31 * result + gesturesEnabled.hashCode()
        return result
    }
}

package eu.darken.sdmse.common.compose.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * D-pad focus memory that survives navigation: the last focused item key is kept in saveable
 * state (per NavEntry via its SaveableStateHolder), so when a screen's composition is disposed
 * under a pushed sub-screen and recreated on pop, the previously focused row regains focus
 * instead of the cursor resetting to the first focusable.
 *
 * The framework alternatives don't cover this: [androidx.compose.ui.focus.focusRestorer] only
 * remembers within one composition lifetime (and is documented in DashboardScreen as unreliable
 * for requester-based/spatial focus entries even then). This is the dashboard's explicit
 * per-item FocusRequester + onFocusEvent pattern, made saveable and reusable.
 *
 * Restore semantics:
 * - Proactive: the remembered row requests focus as soon as it composes after re-entry — no
 *   key press needed. Standard TV behavior.
 * - Input-mode gated: restore only happens while the input mode is [InputMode.Keyboard]
 *   (D-pad / hardware keyboard). The mode is re-checked over a short frame window rather than
 *   once, so a mode flip a frame late isn't missed; touch users never see a focus ring appear.
 * - Bounded: a restore is claimed exactly once by the item carrying the remembered key, and
 *   the pending restore expires after [RESTORE_WINDOW_FRAMES] frames. A remembered row that
 *   no longer exists (conditional rows toggled off while the screen was away) therefore can't
 *   steal focus when it reappears later.
 */
@Stable
class DpadFocusMemory internal constructor(
    private val lastFocusedKeyState: MutableState<String>,
) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    // One-shot: set from the restored key at (re)creation, consumed by the first matching item.
    private var pendingRestoreKey: String? = lastFocusedKeyState.value.takeIf { it.isNotEmpty() }

    internal fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    internal fun noteFocus(key: String) {
        lastFocusedKeyState.value = key
    }

    /**
     * Atomically takes the pending restore for [key]. Claiming (not focus-gain) clears the
     * pending state: incidental default focus on another row right after re-entry must not
     * cancel the restore, but once the target row has claimed it, no later recomposition may
     * trigger it again.
     */
    internal fun claimRestore(key: String): Boolean {
        if (pendingRestoreKey != key) return false
        pendingRestoreKey = null
        return true
    }

    internal fun expireRestore() {
        pendingRestoreKey = null
    }
}

val LocalDpadFocusMemory = compositionLocalOf<DpadFocusMemory?> { null }

@Composable
fun rememberDpadFocusMemory(): DpadFocusMemory {
    val lastFocusedKey = rememberSaveable { mutableStateOf("") }
    val memory = remember { DpadFocusMemory(lastFocusedKey) }
    LaunchedEffect(memory) {
        repeat(RESTORE_WINDOW_FRAMES) { withFrameNanos { } }
        memory.expireRestore()
    }
    return memory
}

/**
 * Registers this node as a focus-memory item under [key]. No-op when no [LocalDpadFocusMemory]
 * is provided (previews, non-scaffold hosts).
 *
 * Must be applied BEFORE the modifier that creates the focus target (e.g. `combinedClickable`),
 * so the requester and focus observer attach to that target.
 */
@Composable
fun Modifier.dpadFocusMemoryItem(key: String): Modifier {
    val memory = LocalDpadFocusMemory.current ?: return this
    val inputModeManager = LocalInputModeManager.current
    val requester = remember(memory, key) { memory.requesterFor(key) }

    LaunchedEffect(memory, key) {
        if (!memory.claimRestore(key)) return@LaunchedEffect
        repeat(RESTORE_WINDOW_FRAMES) {
            if (inputModeManager.inputMode == InputMode.Keyboard) {
                // Throws if the node isn't attached yet (fresh lazy item) — retry next frame.
                if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    return this
        .focusRequester(requester)
        .onFocusEvent { if (it.hasFocus) memory.noteFocus(key) }
}

private const val RESTORE_WINDOW_FRAMES = 10

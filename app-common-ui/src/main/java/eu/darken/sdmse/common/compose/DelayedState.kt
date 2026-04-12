package eu.darken.sdmse.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Returns a delayed boolean state that becomes true only after [delayMs] milliseconds
 * when [showWhen] returns true for the given [value].
 *
 * Useful for preventing UI flicker when content appears briefly (e.g., loading indicators).
 */
@Composable
fun <T> rememberDelayedState(
    value: T,
    delayMs: Long = 200,
    showWhen: (T) -> Boolean = { it != null },
): Boolean {
    var showDelayed by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (showWhen(value)) {
            delay(delayMs)
            showDelayed = true
        } else {
            showDelayed = false
        }
    }
    return showDelayed
}

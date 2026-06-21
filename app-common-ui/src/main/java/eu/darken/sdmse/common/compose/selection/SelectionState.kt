package eu.darken.sdmse.common.compose.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Multi-selection state for tool list/detail screens, designed so that toggling a single row only
 * recomposes that row instead of the whole visible window.
 *
 * The previous helper handed out a raw `MutableState<Set<T>>`, and screens read `selection.contains(id)`
 * and `selection.isNotEmpty()` directly inside their `items {}` lambda. Every visible item then subscribed
 * to the one set state, so a single toggle invalidated every item and re-ran its expensive leaf work
 * (thumbnails, file-size/date formatting, plurals). This holder replaces that pattern:
 *
 * - [isSelected] exposes per-id membership as a `derivedStateOf`, so only rows whose membership actually
 *   flips recompose. This isolation holds while selection is already active; entering selection mode
 *   (first item) or clearing the last item flips [isActive] and legitimately recomposes every row that
 *   reacts to selection mode.
 * - [isActive] and [count] are `derivedStateOf`, so a container or top bar reading them only recomposes on
 *   the empty<->non-empty transition / count change, not on every toggle.
 *
 * Backed by [rememberSelectionState] / [rememberSaveable] so it survives configuration changes and process
 * death (every selection id type is Bundle-saveable, as with the prior helper).
 */
@Stable
class SelectionState<T : Any>(initial: Set<T> = emptySet()) {

    // Defensive copy: never alias a caller's (possibly mutable) set into snapshot state.
    private var _selected by mutableStateOf(initial.toSet())

    /**
     * Snapshot of the current selection. Reading this subscribes to ANY selection change, so use it at
     * event time (action callbacks, click handlers) — not to derive per-row UI during composition, which
     * would reintroduce the whole-list invalidation this holder exists to avoid.
     */
    val selected: Set<T> get() = _selected

    /** `true` while anything is selected. Notifies only on the empty <-> non-empty transition. */
    val isActive: Boolean by derivedStateOf { _selected.isNotEmpty() }

    /** Number of selected items. Notifies only when the count changes (e.g. top-bar "N selected"). */
    val count: Int by derivedStateOf { _selected.size }

    /**
     * Per-id membership as a Compose-isolated read. Only rows whose membership flips recompose.
     * Must be called from composable scope (e.g. inside a Lazy item).
     */
    @Composable
    fun isSelected(id: T): Boolean {
        val state = remember(this, id) { derivedStateOf { id in _selected } }
        return state.value
    }

    /**
     * Event-time membership check (click handlers). Does NOT create a composition subscription — do not
     * call this in composition to derive per-row state; use [isSelected] there.
     */
    fun contains(id: T): Boolean = id in _selected

    fun toggle(id: T) {
        _selected = if (id in _selected) _selected - id else _selected + id
    }

    fun select(id: T) {
        _selected = _selected + id
    }

    fun deselect(id: T) {
        _selected = _selected - id
    }

    fun setSelection(ids: Set<T>) {
        _selected = ids.toSet()
    }

    fun clear() {
        _selected = emptySet()
    }

    /** Drop ids that are no longer present (replaces the prune `intersect`); skips a no-op write. */
    fun retainAll(ids: Set<T>) {
        val next = _selected intersect ids
        if (next.size != _selected.size) _selected = next
    }

    companion object {
        fun <T : Any> saver() = listSaver<SelectionState<T>, T>(
            save = { it._selected.toList() },
            restore = { SelectionState(it.toSet()) },
        )
    }
}

@Composable
fun <T : Any> rememberSelectionState(): SelectionState<T> = rememberSaveable(
    saver = SelectionState.saver(),
) { SelectionState() }

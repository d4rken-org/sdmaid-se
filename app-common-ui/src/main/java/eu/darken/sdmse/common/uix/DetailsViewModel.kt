package eu.darken.sdmse.common.uix

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.navigation.mutableState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.lang.Integer.min

fun <T, ID> resolveTarget(
    items: List<T>,
    requestedTarget: ID?,
    lastPosition: Int?,
    identifierOf: (T) -> ID,
    onPositionTracked: (Int) -> Unit,
): ID? {
    val currentIndex = items.indexOfFirst { identifierOf(it) == requestedTarget }
    if (currentIndex != -1) onPositionTracked(currentIndex)

    return when {
        items.isEmpty() -> null
        currentIndex != -1 -> requestedTarget
        lastPosition != null -> identifierOf(items[min(lastPosition, items.size - 1)])
        else -> requestedTarget
    }
}

/**
 * Base for pager-style detail screens (AppCleaner / Deduplicator / CorpseFinder / SystemCleaner)
 * that browse a list of items via [androidx.compose.foundation.pager.HorizontalPager] and need
 * to recover the visible target after process death.
 *
 * Provides:
 * - [routeFlow] — set once via [bindRoute] from the screen's `LaunchedEffect`.
 * - [currentTarget] / [lastPosition] — saved-state-backed pager cursor.
 * - [autoNavUpOnEmpty] — pop back to the list when the tool's data drains.
 *
 * Subclasses still own their tool-specific state composition (combine + sort + [resolveTarget])
 * and their own `events` surface.
 */
abstract class PagedDetailsViewModel<TRoute, TId>(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    tag: String,
) : ViewModel4(dispatcherProvider, tag = tag) {

    private val _routeFlow = MutableStateFlow<TRoute?>(null)
    protected val routeFlow: StateFlow<TRoute?> = _routeFlow

    protected var currentTarget: TId? by handle.mutableState("target")
    protected var lastPosition: Int? by handle.mutableState("position")

    /**
     * Reactive view of [currentTarget], backed by the same saved-state key (`mutableState`'s setter
     * writes through [SavedStateHandle.set], which [SavedStateHandle.getStateFlow] observes). Fold
     * this into the subclass state combine so a pager target change (updatePage / undo-restore)
     * re-resolves the visible page on its own, WITHOUT relying on an incidental upstream re-emit such
     * as a progress tick — important now that progress is decoupled from item production for scan-time
     * perf.
     */
    protected val currentTargetFlow: StateFlow<TId?> = handle.getStateFlow("target", null)

    /** Set the route exactly once. Subsequent calls are no-ops so screen-recompositions don't reset state. */
    fun bindRoute(route: TRoute) {
        if (_routeFlow.value != null) return
        log(tag, INFO) { "bindRoute(${bindRouteLogValue(route)})" }
        _routeFlow.value = route
    }

    /**
     * Override when the route's [toString] is unhelpful (e.g. when it holds a serialized blob).
     * Default is the route itself.
     */
    protected open fun bindRouteLogValue(route: TRoute): Any? = route

    /**
     * Navigate up the first time [hasData] reports `false` after the initial emission.
     * The initial value is dropped so launching the screen before data loads doesn't trigger navUp.
     *
     * Callers typically pass `tool.state.map { it.data?.corpses?.isNotEmpty() == true }` — i.e.
     * `hasData` is `true` only when [Data] is non-null AND non-empty. The legacy
     * `Data?.hasData` extension (`this?.corpses?.isNotEmpty() ?: false`) maps to the same shape,
     * but be wary of passing a flow that maps `null` to `false`: when [performScan] sets
     * internalData briefly to `null` at the start of a refresh, that emission would fire
     * navUp during loading. Pass a flow that distinguishes "loading" (suppress) from
     * "drained" (fire) — see callers using `map { it.data?.corpses?.isEmpty() == false }`
     * for the canonical shape.
     */
    protected fun autoNavUpOnEmpty(hasData: Flow<Boolean>) {
        hasData
            .drop(1)
            .filter { !it }
            .take(1)
            .onEach { navUp() }
            .launchInViewModel()
    }
}

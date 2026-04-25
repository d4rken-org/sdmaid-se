package eu.darken.sdmse.swiper.ui.swipe

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.SwiperSettings
import eu.darken.sdmse.swiper.ui.SwiperStatusRoute
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class SwiperSwipeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val swiper: Swiper,
    private val settings: SwiperSettings,
    private val exclusionManager: ExclusionManager,
    private val viewIntentTool: ViewIntentTool,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    // Nav3 + Hilt SavedStateHandle deserialization is unreliable for routes with multi-arg
    // primitives (the SwiperSwipeRoute.from(handle) crashed on missing 'sessionId' on-device).
    // The Host composable receives the deserialized route from the entry lambda and binds it
    // here once. All session-scoped logic reads from the bound route via routeFlow.
    private val routeFlow = MutableStateFlow<SwiperSwipeRoute?>(null)

    private val sessionId: String? get() = routeFlow.value?.sessionId

    private val currentIndexOverride = MutableStateFlow<Int?>(null)

    private data class UndoEntry(
        val itemId: Long,
        val previousDecision: SwipeDecision,
        val previousIndex: Int,
    )

    private val undoHistory = MutableStateFlow<List<UndoEntry>>(emptyList())

    val events = SingleEventFlow<Event>()

    fun bindRoute(route: SwiperSwipeRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(sessionId=${route.sessionId}, startIndex=${route.startIndex})" }
        routeFlow.value = route
        if (route.startIndex >= 0) currentIndexOverride.value = route.startIndex
        launch {
            if (!swiper.hasSessionLookups(route.sessionId)) {
                log(TAG, WARN) { "Cache miss for session ${route.sessionId} after process death, navigating back to sessions" }
                navToSessions()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State?> = routeFlow.filterNotNull().flatMapLatest { route ->
        val sid = route.sessionId
        combine(
            swiper.getSession(sid),
            swiper.getItemsForSession(sid),
            swiper.getSessionsWithStats(),
            currentIndexOverride,
            settings.swapSwipeDirections.flow,
            settings.showFileDetailsOverlay.flow,
            undoHistory,
            settings.hasShownGestureOverlay.flow,
        ) { session: SwipeSession?,
            items: List<SwipeItem>,
            allSessions: List<Swiper.SessionWithStats>,
            indexOverride: Int?,
            swapDirections: Boolean,
            showDetails: Boolean,
            undoStack: List<UndoEntry>,
            hasShownOverlay: Boolean ->
            // Clear stale override when session was reset to 0 (e.g., after deletion)
            val currentIndex = when {
                session?.currentIndex == 0 && indexOverride != null && indexOverride > 0 -> {
                    currentIndexOverride.value = null
                    0
                }
                else -> indexOverride ?: session?.currentIndex ?: 0
            }.coerceIn(0, maxOf(0, items.size - 1))
            val keepItems = items.filter { it.decision == SwipeDecision.KEEP }
            val deleteItems = items.filter { it.decision == SwipeDecision.DELETE }
            val undecidedItems = items.filter { it.decision == SwipeDecision.UNDECIDED }

            val sessionPosition = allSessions
                .sortedBy { it.session.createdAt }
                .indexOfFirst { it.session.sessionId == sid }
                .let { if (it >= 0) it + 1 else null }

            State(
                session = session,
                items = items,
                currentIndex = currentIndex,
                totalItems = session?.totalItems ?: items.size,
                keepCount = keepItems.size,
                keepSize = keepItems.sumOf { it.lookup.size },
                deleteCount = deleteItems.size,
                deleteSize = deleteItems.sumOf { it.lookup.size },
                undecidedCount = undecidedItems.size,
                undecidedSize = undecidedItems.sumOf { it.lookup.size },
                swapDirections = swapDirections,
                showDetails = showDetails,
                sessionPosition = sessionPosition,
                canUndo = undoStack.isNotEmpty(),
                showGestureOverlay = !hasShownOverlay,
            )
        }
    }.safeStateIn(initialValue = null) { null }

    fun setDecision(itemId: Long, decision: SwipeDecision) = launch {
        log(TAG, INFO) { "setDecision(itemId=$itemId, decision=$decision)" }

        val currentState = state.value
        val currentItem = currentState?.currentItem
        if (currentItem != null && currentItem.id == itemId) {
            val entry = UndoEntry(
                itemId = itemId,
                previousDecision = currentItem.decision,
                previousIndex = currentState.currentIndex,
            )
            undoHistory.value = (undoHistory.value + entry).takeLast(50)
        }

        if (settings.hapticFeedbackEnabled.value()) {
            events.tryEmit(Event.TriggerHapticFeedback)
        }
        swiper.updateDecision(itemId, decision)
        advanceOrNavigate()
    }

    fun skip() = launch {
        log(TAG, INFO) { "skip()" }

        val currentState = state.value ?: return@launch
        val currentItem = currentState.currentItem

        if (currentItem != null) {
            val entry = UndoEntry(
                itemId = currentItem.id,
                previousDecision = currentItem.decision,
                previousIndex = currentState.currentIndex,
            )
            undoHistory.value = (undoHistory.value + entry).takeLast(50)
        }

        if (currentItem != null && currentItem.decision != SwipeDecision.UNDECIDED) {
            log(TAG, INFO) { "skip(): Resetting ${currentItem.id} to UNDECIDED" }
            swiper.updateDecision(currentItem.id, SwipeDecision.UNDECIDED)
        }

        advanceOrNavigate()
    }

    fun undo() = launch {
        val history = undoHistory.value
        if (history.isEmpty()) {
            log(TAG, WARN) { "undo(): No history to undo" }
            return@launch
        }

        val entry = history.last()
        log(TAG, INFO) { "undo(): Restoring itemId=${entry.itemId} to ${entry.previousDecision} at index ${entry.previousIndex}" }

        undoHistory.value = history.dropLast(1)
        swiper.updateDecision(entry.itemId, entry.previousDecision)
        setCurrentIndex(entry.previousIndex)
    }

    private suspend fun advanceOrNavigate() {
        val currentState = state.value ?: return
        val items = currentState.items
        val currentIdx = currentState.currentIndex
        val currentItemId = currentState.currentItem?.id
        val sid = sessionId ?: return

        if (items.isEmpty()) {
            log(TAG, INFO) { "advanceOrNavigate: No items remain, discarding session" }
            swiper.discardSession(sid)
            navToSessions()
            return
        }

        val nextUndecidedIndex = findNextUndecidedIndex(items, currentIdx, currentItemId)

        if (nextUndecidedIndex != null) {
            setCurrentIndex(nextUndecidedIndex)
        } else {
            log(TAG, INFO) { "advanceOrNavigate: No more undecided items, navigating to status" }
            navigateToStatus()
        }
    }

    private fun findNextUndecidedIndex(items: List<SwipeItem>, currentIdx: Int, excludeItemId: Long?): Int? {
        for (i in (currentIdx + 1) until items.size) {
            val item = items[i]
            if (item.decision == SwipeDecision.UNDECIDED && item.id != excludeItemId) return i
        }
        for (i in 0 until currentIdx) {
            val item = items[i]
            if (item.decision == SwipeDecision.UNDECIDED && item.id != excludeItemId) return i
        }
        return null
    }

    fun setCurrentIndex(index: Int) = launch {
        log(TAG, INFO) { "setCurrentIndex(index=$index)" }
        val sid = sessionId ?: return@launch
        currentIndexOverride.value = index
        swiper.updateCurrentIndex(sid, index)
    }

    fun navigateToStatus() {
        val sid = sessionId ?: return
        log(TAG, INFO) { "navigateToStatus()" }
        navTo(SwiperStatusRoute(sessionId = sid))
    }

    private fun navToSessions() {
        navTo(
            destination = SwiperSessionsRoute,
            popUpTo = SwiperSessionsRoute,
            inclusive = true,
        )
    }

    fun dismissGestureOverlay() = launch {
        log(TAG, INFO) { "dismissGestureOverlay()" }
        settings.hasShownGestureOverlay.value(true)
    }

    fun openExternally(item: SwipeItem) = launch {
        log(TAG, INFO) { "openExternally(${item.lookup.lookedUp})" }
        val intent = viewIntentTool.create(item.lookup)
        if (intent != null) {
            events.tryEmit(Event.OpenExternally(intent))
        } else {
            events.tryEmit(Event.ShowOpenNotSupported)
        }
    }

    fun excludeAndRemove(item: SwipeItem) = launch {
        log(TAG, INFO) { "excludeAndRemove(${item.lookup.lookedUp})" }
        val exclusion = PathExclusion(
            path = item.lookup.lookedUp,
            tags = setOf(Exclusion.Tag.SWIPER),
        )
        exclusionManager.save(exclusion)
        swiper.removeItem(item.id)
        advanceOrNavigate()
    }

    sealed interface Event {
        data object TriggerHapticFeedback : Event
        data class OpenExternally(val intent: Intent) : Event
        data object ShowOpenNotSupported : Event
    }

    data class State(
        val session: SwipeSession?,
        val items: List<SwipeItem>,
        val currentIndex: Int,
        val totalItems: Int,
        val keepCount: Int,
        val keepSize: Long,
        val deleteCount: Int,
        val deleteSize: Long,
        val undecidedCount: Int,
        val undecidedSize: Long,
        val swapDirections: Boolean,
        val showDetails: Boolean,
        val sessionPosition: Int?,
        val canUndo: Boolean,
        val showGestureOverlay: Boolean,
    ) {
        val currentItem: SwipeItem? = items.getOrNull(currentIndex)
        val nextItem: SwipeItem? = items.getOrNull(currentIndex + 1) ?: items.firstOrNull { it != currentItem }
        val currentItemOriginalIndex: Int? = currentItem?.itemIndex
        val progressPercent: Int = if (totalItems > 0) ((keepCount + deleteCount) * 100 / totalItems) else 0
        val sessionLabel: String? = session?.label
    }

    companion object {
        private val TAG = logTag("Swiper", "Swipe", "ViewModel")
    }
}

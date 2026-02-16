package eu.darken.sdmse.swiper.ui.swipe

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.SwiperSettings
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class SwiperSwipeViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val swiper: Swiper,
    private val settings: SwiperSettings,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SwiperSwipeFragmentArgs>()
    private val sessionId: String = navArgs.sessionId
    private val startIndex: Int = navArgs.startIndex

    private val currentIndexOverride = MutableStateFlow<Int?>(
        if (startIndex >= 0) startIndex else null
    )

    private data class UndoEntry(
        val itemId: Long,
        val previousDecision: SwipeDecision,
        val previousIndex: Int,
    )

    private val undoHistory = MutableStateFlow<List<UndoEntry>>(emptyList())

    val events = SingleLiveEvent<SwiperSwipeEvents>()

    init {
        launch {
            if (!swiper.hasSessionLookups(sessionId)) {
                log(TAG, WARN) { "Cache miss for session $sessionId after process death, navigating back to sessions" }
                events.postValue(SwiperSwipeEvents.NavigateToSessions)
            }
        }
    }

    val state = combine(
        swiper.getSession(sessionId),
        swiper.getItemsForSession(sessionId),
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

        // Calculate session position (1-based, sorted by creation date)
        val sessionPosition = allSessions
            .sortedBy { it.session.createdAt }
            .indexOfFirst { it.session.sessionId == sessionId }
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
    }.asLiveData2()

    fun setDecision(itemId: Long, decision: SwipeDecision) = launch {
        log(TAG, INFO) { "setDecision(itemId=$itemId, decision=$decision)" }

        // Store undo entry before applying decision
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
            events.postValue(SwiperSwipeEvents.TriggerHapticFeedback)
        }
        swiper.updateDecision(itemId, decision)
        advanceOrNavigate()
    }

    fun skip() = launch {
        log(TAG, INFO) { "skip()" }

        val currentState = state.value ?: return@launch
        val currentItem = currentState.currentItem

        // Store undo entry before skipping
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

        // If no items remain, discard session and go back to sessions
        if (items.isEmpty()) {
            log(TAG, INFO) { "advanceOrNavigate: No items remain, discarding session" }
            swiper.discardSession(sessionId)
            events.postValue(SwiperSwipeEvents.NavigateToSessions)
            return
        }

        // Find next undecided item (excluding the current item which may have just been decided)
        val nextUndecidedIndex = findNextUndecidedIndex(items, currentIdx, currentItemId)

        if (nextUndecidedIndex != null) {
            setCurrentIndex(nextUndecidedIndex)
        } else {
            // No more undecided items - navigate to review screen
            log(TAG, INFO) { "advanceOrNavigate: No more undecided items, navigating to status" }
            navigateToStatus()
        }
    }

    private fun findNextUndecidedIndex(items: List<SwipeItem>, currentIdx: Int, excludeItemId: Long?): Int? {
        // First, search forward from current position
        for (i in (currentIdx + 1) until items.size) {
            val item = items[i]
            if (item.decision == SwipeDecision.UNDECIDED && item.id != excludeItemId) return i
        }
        // Then, search from beginning up to current position
        for (i in 0 until currentIdx) {
            val item = items[i]
            if (item.decision == SwipeDecision.UNDECIDED && item.id != excludeItemId) return i
        }
        return null
    }

    fun setCurrentIndex(index: Int) = launch {
        log(TAG, INFO) { "setCurrentIndex(index=$index)" }
        currentIndexOverride.value = index
        swiper.updateCurrentIndex(sessionId, index)
    }

    fun navigateToStatus() {
        log(TAG, INFO) { "navigateToStatus()" }
        SwiperSwipeFragmentDirections.actionSwiperSwipeFragmentToSwiperStatusFragment(sessionId).navigate()
    }

    fun dismissGestureOverlay() = launch {
        log(TAG, INFO) { "dismissGestureOverlay()" }
        settings.hasShownGestureOverlay.value(true)
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
        val currentItemOriginalIndex: Int? = currentItem?.itemIndex
        val progressPercent: Int = if (totalItems > 0) ((keepCount + deleteCount) * 100 / totalItems) else 0
        val sessionLabel: String? = session?.label
    }

    companion object {
        private val TAG = logTag("Swiper", "Swipe", "ViewModel")
    }
}

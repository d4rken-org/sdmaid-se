package eu.darken.sdmse.swiper.ui.swipe

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class SwiperSwipeViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    // Decisions advance the UI optimistically and persist in the background; the writes must outlive
    // vmScope so leaving the screen mid-swipe (or a rapid burst right before back) can't drop them.
    // Hence @AppScope rather than vmScope for the persistence queue only — all render flows stay on
    // vmScope. Errors in those ops are handled in enqueuePersist (ViewModel4's handler covers vmScope).
    @AppScope private val appScope: CoroutineScope,
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

    // The visible cursor. Set synchronously by the action methods (on the main thread) so the next
    // card promotes within a frame, decoupled from the background DB writes.
    private val currentIndexOverride = MutableStateFlow<Int?>(null)

    // Optimistic decisions applied on top of the DB items until Room catches up. Lets counts/stamps
    // and next-card selection reflect a swipe instantly; reconciled (entry dropped) once the DB row
    // shows the matching decision. Keyed by itemId.
    private val pendingDecisions = MutableStateFlow<Map<Long, SwipeDecision>>(emptyMap())

    private data class UndoEntry(
        val itemId: Long,
        val previousDecision: SwipeDecision,
        val previousIndex: Int,
    )

    private val undoHistory = MutableStateFlow<List<UndoEntry>>(emptyList())

    // Single eager items source shared by both the render state and the synchronous action hot-path,
    // so a swipe never needs a DB read to compute the next index.
    private val itemsState: StateFlow<List<SwipeItem>> = routeFlow.filterNotNull()
        .flatMapLatest { swiper.getItemsForSession(it.sessionId) }
        .stateIn(vmScope, SharingStarted.Eagerly, emptyList())

    // Eager session source, shared by the render state and the synchronous index fallback so the
    // action path resolves the same currentIndex the UI shows even before the override is set.
    private val sessionState: StateFlow<SwipeSession?> = routeFlow.filterNotNull()
        .flatMapLatest { swiper.getSession(it.sessionId) }
        .stateIn(vmScope, SharingStarted.Eagerly, null)

    // Number of decision writes not yet persisted. Pending optimistic decisions are only reconciled
    // against the DB once this hits 0, so a burst (e.g. keep→undo→keep on one item) can't have an
    // earlier DB echo prematurely clear the latest pending value.
    private val inFlightWrites = MutableStateFlow(0)

    // Serializes background persistence so writes keep their submission order (a rapid decide→undo on
    // the same item can't reorder). Held on the app scope so writes survive the screen being left
    // mid-swipe. kotlinx Mutex is fair, and enqueuePersist starts UNDISPATCHED, so lock acquisition
    // happens in call order.
    private val persistMutex = Mutex()

    val events = SingleEventFlow<Event>()

    init {
        // Drop a pending optimistic decision once the DB reflects it AND the write queue has drained,
        // so an in-flight later write can't be undone by an earlier echo. (kotlinx 2-arg combine —
        // the project's combine helper starts at 3 args.)
        kotlinx.coroutines.flow.combine(itemsState, inFlightWrites) { dbItems, inFlight ->
            dbItems to inFlight
        }
            .onEach { (dbItems, inFlight) -> if (inFlight == 0) reconcilePending(dbItems) }
            .launchIn(vmScope)
    }

    fun bindRoute(route: SwiperSwipeRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(sessionId=${route.sessionId}, startIndex=${route.startIndex})" }
        routeFlow.value = route
        if (route.startIndex >= 0) currentIndexOverride.value = route.startIndex
        launch {
            // Resolve the cursor eagerly so the synchronous action path has an authoritative value
            // before the user can physically swipe (only when no explicit startIndex was given).
            if (currentIndexOverride.value == null) {
                val resolved = swiper.getSession(route.sessionId).first()?.currentIndex ?: 0
                if (currentIndexOverride.value == null) currentIndexOverride.value = resolved
            }
            if (!swiper.hasSessionLookups(route.sessionId)) {
                log(TAG, WARN) { "Cache miss for session ${route.sessionId} after process death, navigating back to sessions" }
                navToSessions()
            }
        }
    }

    val state: StateFlow<State?> = routeFlow.filterNotNull().flatMapLatest { route ->
        val sid = route.sessionId
        combine(
            sessionState,
            itemsState,
            swiper.activeSessions,
            currentIndexOverride,
            settings.swapSwipeDirections.flow,
            settings.showFileDetailsOverlay.flow,
            undoHistory,
            pendingDecisions,
        ) { session: SwipeSession?,
            rawItems: List<SwipeItem>,
            allSessions: List<SwipeSession>,
            indexOverride: Int?,
            swapDirections: Boolean,
            showDetails: Boolean,
            undoStack: List<UndoEntry>,
            pending: Map<Long, SwipeDecision> ->
            // Apply optimistic decisions so the UI reflects a swipe instantly, before the DB write.
            val items = if (pending.isEmpty()) {
                rawItems
            } else {
                rawItems.map { item -> pending[item.id]?.let { item.copy(decision = it) } ?: item }
            }
            // Override (set by bindRoute / actions) takes precedence over the session's persistent
            // currentIndex. After a partial delete the session resets to 0 but the override may point
            // past the now-shorter items list — coerceIn handles that.
            val currentIndex = (indexOverride ?: session?.currentIndex ?: 0)
                .coerceIn(0, maxOf(0, items.size - 1))

            // Single pass over the (optimistic) items for the bucket counts/sizes.
            var keepCount = 0
            var keepSize = 0L
            var deleteCount = 0
            var deleteSize = 0L
            var undecidedCount = 0
            var undecidedSize = 0L
            for (item in items) {
                when (item.decision) {
                    SwipeDecision.KEEP -> {
                        keepCount++
                        keepSize += item.lookup.size
                    }
                    SwipeDecision.DELETE -> {
                        deleteCount++
                        deleteSize += item.lookup.size
                    }
                    SwipeDecision.UNDECIDED -> {
                        undecidedCount++
                        undecidedSize += item.lookup.size
                    }
                    else -> Unit
                }
            }

            val sessionPosition = allSessions
                .sortedBy { it.createdAt }
                .indexOfFirst { it.sessionId == sid }
                .let { if (it >= 0) it + 1 else null }

            State(
                session = session,
                items = items,
                currentIndex = currentIndex,
                totalItems = session?.totalItems ?: items.size,
                keepCount = keepCount,
                keepSize = keepSize,
                deleteCount = deleteCount,
                deleteSize = deleteSize,
                undecidedCount = undecidedCount,
                undecidedSize = undecidedSize,
                swapDirections = swapDirections,
                showDetails = showDetails,
                sessionPosition = sessionPosition,
                canUndo = undoStack.isNotEmpty(),
            )
        }
    }.safeStateIn(initialValue = null) { null }

    private fun effectiveDecision(item: SwipeItem): SwipeDecision =
        pendingDecisions.value[item.id] ?: item.decision

    private fun currentIndexNow(items: List<SwipeItem>): Int =
        (currentIndexOverride.value ?: sessionState.value?.currentIndex ?: 0)
            .coerceIn(0, maxOf(0, items.size - 1))

    private fun reconcilePending(dbItems: List<SwipeItem>) {
        pendingDecisions.update { pending ->
            if (pending.isEmpty()) return@update pending
            pending.filterNot { (id, decision) ->
                dbItems.firstOrNull { it.id == id }?.decision == decision
            }
        }
    }

    /**
     * @param tracksDecision true for ops that write an item decision — keeps [inFlightWrites] raised
     * until the write lands so optimistic-decision reconciliation waits for the queue to drain.
     */
    private fun enqueuePersist(tracksDecision: Boolean = false, op: suspend () -> Unit) {
        // Retain synchronously (main thread) so the counter rises in submission order.
        if (tracksDecision) inFlightWrites.update { it + 1 }
        // UNDISPATCHED so lock acquisition is enqueued synchronously in submission order; the fair
        // Mutex then runs the ops one-at-a-time in that order on the app scope.
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                persistMutex.withLock {
                    try {
                        op()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Persist op failed: ${e.asLog()}" }
                    }
                }
            } finally {
                if (tracksDecision) inFlightWrites.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    fun setDecision(itemId: Long, decision: SwipeDecision) {
        val items = itemsState.value
        val currentIndex = currentIndexNow(items)
        val currentItem = items.getOrNull(currentIndex)
        // Ignore stale/duplicate commits: with instant advance, an old card's callback can fire
        // against the new cursor.
        if (currentItem == null || currentItem.id != itemId) {
            log(TAG, WARN) { "setDecision($itemId, $decision) ignored: not the current item (${currentItem?.id})" }
            return
        }
        log(TAG, INFO) { "setDecision(itemId=$itemId, decision=$decision)" }

        undoHistory.value = (
            undoHistory.value + UndoEntry(itemId, effectiveDecision(currentItem), currentIndex)
            ).takeLast(UNDO_HISTORY_LIMIT)

        // Optimistic + cursor advance — synchronous, before any IO.
        pendingDecisions.update { it + (itemId to decision) }
        val next = nextUndecidedIndex(items, pendingDecisions.value, currentIndex, itemId)
        if (next != null) currentIndexOverride.value = next

        val sid = sessionId
        enqueuePersist(tracksDecision = true) {
            swiper.updateDecision(itemId, decision)
            if (next != null && sid != null) swiper.updateCurrentIndex(sid, next)
        }

        launch {
            if (settings.hapticFeedbackEnabled.value()) events.tryEmit(Event.TriggerHapticFeedback)
        }

        if (next == null) navigateToStatus()
    }

    fun skip(itemId: Long) {
        val items = itemsState.value
        if (items.isEmpty()) {
            log(TAG, INFO) { "skip(): No items remain, discarding session" }
            val sid = sessionId ?: return
            enqueuePersist { swiper.discardSession(sid) }
            navToSessions()
            return
        }
        val currentIndex = currentIndexNow(items)
        val currentItem = items.getOrNull(currentIndex)
        // Same stale/duplicate-commit guard as setDecision: with instant advance a late skip callback
        // (gesture + button, or a double tap) could otherwise skip whatever is current at VM time.
        if (currentItem == null || currentItem.id != itemId) {
            log(TAG, WARN) { "skip($itemId) ignored: not the current item (${currentItem?.id})" }
            return
        }
        log(TAG, INFO) { "skip(): item=${currentItem.id}" }

        undoHistory.value = (
            undoHistory.value + UndoEntry(currentItem.id, effectiveDecision(currentItem), currentIndex)
            ).takeLast(UNDO_HISTORY_LIMIT)

        val needsReset = effectiveDecision(currentItem) != SwipeDecision.UNDECIDED
        if (needsReset) pendingDecisions.update { it + (currentItem.id to SwipeDecision.UNDECIDED) }

        // Exclude the just-skipped item so we don't immediately land back on it.
        val next = nextUndecidedIndex(items, pendingDecisions.value, currentIndex, currentItem.id)
        if (next != null) currentIndexOverride.value = next

        val sid = sessionId
        enqueuePersist(tracksDecision = needsReset) {
            if (needsReset) swiper.updateDecision(currentItem.id, SwipeDecision.UNDECIDED)
            if (next != null && sid != null) swiper.updateCurrentIndex(sid, next)
        }

        if (next == null) navigateToStatus()
    }

    fun undo() {
        val history = undoHistory.value
        if (history.isEmpty()) {
            log(TAG, WARN) { "undo(): No history to undo" }
            return
        }
        val entry = history.last()
        log(TAG, INFO) { "undo(): Restoring itemId=${entry.itemId} to ${entry.previousDecision} at index ${entry.previousIndex}" }

        undoHistory.value = history.dropLast(1)
        pendingDecisions.update { it + (entry.itemId to entry.previousDecision) }
        currentIndexOverride.value = entry.previousIndex

        val sid = sessionId
        enqueuePersist(tracksDecision = true) {
            swiper.updateDecision(entry.itemId, entry.previousDecision)
            if (sid != null) swiper.updateCurrentIndex(sid, entry.previousIndex)
        }
    }

    fun setCurrentIndex(index: Int) {
        val sid = sessionId ?: return
        log(TAG, INFO) { "setCurrentIndex(index=$index)" }
        currentIndexOverride.value = index
        enqueuePersist { swiper.updateCurrentIndex(sid, index) }
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
        // Non-optimistic: the exclusion save can fail, so only advance after both side effects land.
        exclusionManager.save(exclusion)
        pendingDecisions.update { it - item.id }
        undoHistory.update { stack -> stack.filterNot { it.itemId == item.id } }
        swiper.removeItem(item.id)

        val sid = sessionId ?: return@launch
        val items = swiper.getItemsForSession(sid).first()
        if (items.isEmpty()) {
            log(TAG, INFO) { "excludeAndRemove: No items remain, discarding session" }
            swiper.discardSession(sid)
            navToSessions()
            return@launch
        }
        val currentIndex = currentIndexNow(items)
        val next = nextUndecidedIndex(items, pendingDecisions.value, currentIndex, items.getOrNull(currentIndex)?.id)
        if (next != null) {
            currentIndexOverride.value = next
            enqueuePersist { swiper.updateCurrentIndex(sid, next) }
        } else {
            navigateToStatus()
        }
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
    ) {
        val currentItem: SwipeItem? = items.getOrNull(currentIndex)
        // Strict next item only (legacy parity). The previous `?: firstOrNull { it != current }`
        // fallback showed an arbitrary earlier (often already-decided) card as the back-card when
        // the current item was last — misrepresenting what will actually be processed next.
        val nextItem: SwipeItem? = items.getOrNull(currentIndex + 1)
        val progressPercent: Int = if (totalItems > 0) ((keepCount + deleteCount) * 100 / totalItems) else 0
        val sessionLabel: String? = session?.label
    }

    companion object {
        private val TAG = logTag("Swiper", "Swipe", "ViewModel")
        private const val UNDO_HISTORY_LIMIT = 50
    }
}

/**
 * Next undecided item index starting after [fromIndex], wrapping around to the start, skipping items
 * that are decided (either persisted or optimistically via [pending]) and [excludeItemId]. Returns
 * null when no undecided item remains.
 */
internal fun nextUndecidedIndex(
    items: List<SwipeItem>,
    pending: Map<Long, SwipeDecision>,
    fromIndex: Int,
    excludeItemId: Long?,
): Int? {
    fun isUndecided(item: SwipeItem) = (pending[item.id] ?: item.decision) == SwipeDecision.UNDECIDED
    for (i in (fromIndex + 1) until items.size) {
        val item = items[i]
        if (isUndecided(item) && item.id != excludeItemId) return i
    }
    for (i in 0 until fromIndex.coerceAtMost(items.size)) {
        val item = items[i]
        if (isUndecided(item) && item.id != excludeItemId) return i
    }
    return null
}

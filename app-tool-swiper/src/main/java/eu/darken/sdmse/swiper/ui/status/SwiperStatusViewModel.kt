package eu.darken.sdmse.swiper.ui.status

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.tasks.SwiperDeleteTask
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.swiper.ui.SwiperStatusRoute
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class SwiperStatusViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val swiper: Swiper,
    private val taskSubmitter: TaskSubmitter,
    private val exclusionManager: ExclusionManager,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    // Route deserialization via SavedStateHandle.toRoute<>() crashes with MissingFieldException
    // even for plain `String` primitives — the Picker pattern (route through entry lambda) avoids it.
    private val routeFlow = MutableStateFlow<SwiperStatusRoute?>(null)

    private val sessionId: String? get() = routeFlow.value?.sessionId

    val events = SingleEventFlow<Event>()

    fun bindRoute(route: SwiperStatusRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(sessionId=${route.sessionId})" }
        routeFlow.value = route
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = routeFlow.filterNotNull().flatMapLatest { route ->
        val sid = route.sessionId
        combine(
            swiper.getSession(sid),
            swiper.getItemsForSession(sid),
            swiper.progress,
        ) { session: SwipeSession?, items: List<SwipeItem>, progress ->
            val keepCount = items.count { it.decision == SwipeDecision.KEEP }
            val deleteCount = items.count { it.decision == SwipeDecision.DELETE || it.decision == SwipeDecision.DELETE_FAILED }
            val undecidedCount = items.count { it.decision == SwipeDecision.UNDECIDED }
            val deletedCount = items.count { it.decision == SwipeDecision.DELETED }
            val keepSize = items.filter { it.decision == SwipeDecision.KEEP }.sumOf { it.lookup.size }
            val deleteSize = items
                .filter { it.decision == SwipeDecision.DELETE || it.decision == SwipeDecision.DELETE_FAILED }
                .sumOf { it.lookup.size }
            val undecidedSize = items.filter { it.decision == SwipeDecision.UNDECIDED }.sumOf { it.lookup.size }

            State(
                items = items,
                keepCount = keepCount,
                deleteCount = deleteCount,
                undecidedCount = undecidedCount,
                deletedCount = deletedCount,
                keepSize = keepSize,
                deleteSize = deleteSize,
                undecidedSize = undecidedSize,
                isProcessing = progress != null,
                alreadyKeptCount = session?.keptCount ?: 0,
                alreadyDeletedCount = session?.deletedCount ?: 0,
            )
        }
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun resetDecision(itemId: Long) = launch {
        log(TAG, INFO) { "resetDecision($itemId)" }
        swiper.updateDecision(itemId, SwipeDecision.UNDECIDED)
    }

    fun markKeep(itemId: Long) = launch {
        log(TAG, INFO) { "markKeep($itemId)" }
        swiper.updateDecision(itemId, SwipeDecision.KEEP)
    }

    fun markDelete(itemId: Long) = launch {
        log(TAG, INFO) { "markDelete($itemId)" }
        swiper.updateDecision(itemId, SwipeDecision.DELETE)
    }

    fun navigateToItem(itemId: Long) {
        log(TAG, INFO) { "navigateToItem($itemId)" }
        val sid = sessionId ?: return
        val currentItems = state.value.items
        val currentPosition = currentItems.indexOfFirst { it.id == itemId }
        if (currentPosition < 0) return
        navTo(
            destination = SwiperSwipeRoute(sessionId = sid, startIndex = currentPosition),
            popUpTo = SwiperStatusRoute(sid),
            inclusive = true,
        )
    }

    fun finalize() = launch {
        log(TAG, INFO) { "finalize()" }
        val sid = sessionId ?: return@launch
        taskSubmitter.submit(SwiperDeleteTask(sessionId = sid))

        val sessionStillExists = swiper.getSession(sid).first() != null
        if (!sessionStillExists) {
            navToSessions()
        }
    }

    fun retryFailed(itemId: Long) = launch {
        log(TAG, INFO) { "retryFailed($itemId)" }
        swiper.retryFailedItem(itemId)
    }

    fun retryAllFailed() = launch {
        log(TAG, INFO) { "retryAllFailed()" }
        val sid = sessionId ?: return@launch
        swiper.retryAllFailed(sid)
    }

    fun done() {
        log(TAG, INFO) { "done()" }
        navToSessions()
    }

    fun excludeAndRemove(items: Collection<SwipeItem>) = launch {
        log(TAG, INFO) { "excludeAndRemove(${items.size} items)" }
        items.forEach { item ->
            val exclusion = PathExclusion(
                path = item.lookup.lookedUp,
                tags = setOf(Exclusion.Tag.SWIPER),
            )
            exclusionManager.save(exclusion)
            swiper.removeItem(item.id)
        }
    }

    fun updateDecisions(items: Collection<SwipeItem>, decision: SwipeDecision) = launch {
        log(TAG, INFO) { "updateDecisions(${items.size} items → $decision)" }
        items.forEach { item ->
            swiper.updateDecision(item.id, decision)
        }
    }

    private fun navToSessions() {
        navTo(
            destination = SwiperSessionsRoute,
            popUpTo = SwiperSessionsRoute,
            inclusive = true,
        )
    }

    data class State(
        val items: List<SwipeItem> = emptyList(),
        val keepCount: Int = 0,
        val deleteCount: Int = 0,
        val undecidedCount: Int = 0,
        val deletedCount: Int = 0,
        val keepSize: Long = 0,
        val deleteSize: Long = 0,
        val undecidedSize: Long = 0,
        val isProcessing: Boolean = false,
        val alreadyKeptCount: Int = 0,
        val alreadyDeletedCount: Int = 0,
    ) {
        val canFinalize: Boolean = !isProcessing
        val canDone: Boolean = deletedCount > 0 && deleteCount == 0 && !isProcessing
        val hasProcessedItems: Boolean = alreadyKeptCount > 0 || alreadyDeletedCount > 0

        val finalizeAction: FinalizeAction = when {
            deletedCount > 0 && deleteCount == 0 -> FinalizeAction.DONE
            deleteCount > 0 -> FinalizeAction.DELETE
            keepCount > 0 -> FinalizeAction.APPLY
            else -> FinalizeAction.HIDDEN
        }
    }

    enum class FinalizeAction { HIDDEN, DELETE, APPLY, DONE }

    sealed interface Event

    companion object {
        private val TAG = logTag("Swiper", "Status", "ViewModel")
    }
}

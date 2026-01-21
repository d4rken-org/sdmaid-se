package eu.darken.sdmse.swiper.ui.status

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.tasks.SwiperDeleteTask
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class SwiperStatusViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val swiper: Swiper,
    private val taskManager: TaskManager,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SwiperStatusFragmentArgs>()
    private val sessionId: String = navArgs.sessionId

    val events = SingleLiveEvent<SwiperStatusEvents>()

    val state = combine(
        swiper.getItemsForSession(sessionId),
        swiper.progress,
    ) { items, progress ->
        val keepCount = items.count { it.decision == SwipeDecision.KEEP }
        val deleteCount = items.count { it.decision == SwipeDecision.DELETE || it.decision == SwipeDecision.DELETE_FAILED }
        val undecidedCount = items.count { it.decision == SwipeDecision.UNDECIDED }
        val deletedCount = items.count { it.decision == SwipeDecision.DELETED }
        val keepSize = items.filter { it.decision == SwipeDecision.KEEP }.sumOf { it.lookup.size }
        val deleteSize = items.filter { it.decision == SwipeDecision.DELETE || it.decision == SwipeDecision.DELETE_FAILED }.sumOf { it.lookup.size }
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
        )
    }.asLiveData2()

    fun resetDecision(itemId: Long) = launch {
        log(TAG, INFO) { "resetDecision(itemId=$itemId)" }
        swiper.updateDecision(itemId, SwipeDecision.UNDECIDED)
    }

    fun markKeep(itemId: Long) = launch {
        log(TAG, INFO) { "markKeep(itemId=$itemId)" }
        swiper.updateDecision(itemId, SwipeDecision.KEEP)
    }

    fun markDelete(itemId: Long) = launch {
        log(TAG, INFO) { "markDelete(itemId=$itemId)" }
        swiper.updateDecision(itemId, SwipeDecision.DELETE)
    }

    fun navigateToItem(itemId: Long) {
        log(TAG, INFO) { "navigateToItem(itemId=$itemId)" }
        val currentItems = state.value?.items ?: return
        val currentPosition = currentItems.indexOfFirst { it.id == itemId }
        if (currentPosition < 0) return
        SwiperStatusFragmentDirections.actionSwiperStatusFragmentToSwiperSwipeFragment(
            sessionId = sessionId,
            startIndex = currentPosition,
        ).navigate()
    }

    fun finalize() = launch {
        log(TAG, INFO) { "finalize()" }
        taskManager.submit(SwiperDeleteTask(sessionId = sessionId))

        // Navigate away only if session was cleaned up (no longer exists)
        // If undecided items remain, session still exists and we stay on screen
        val sessionStillExists = swiper.getSession(sessionId).first() != null
        if (!sessionStillExists) {
            events.postValue(SwiperStatusEvents.NavigateToSessions)
        }
        // Otherwise stay on screen - state will update reactively
    }

    fun retryFailed(itemId: Long) = launch {
        log(TAG, INFO) { "retryFailed(itemId=$itemId)" }
        swiper.retryFailedItem(itemId)
    }

    fun retryAllFailed() = launch {
        log(TAG, INFO) { "retryAllFailed()" }
        swiper.retryAllFailed(sessionId)
    }

    fun done() {
        log(TAG, INFO) { "done()" }
        events.postValue(SwiperStatusEvents.NavigateToSessions)
    }

    fun excludeAndRemove(items: List<SwipeItem>) = launch {
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

    data class State(
        val items: List<SwipeItem>,
        val keepCount: Int,
        val deleteCount: Int,
        val undecidedCount: Int,
        val deletedCount: Int,
        val keepSize: Long,
        val deleteSize: Long,
        val undecidedSize: Long,
        val isProcessing: Boolean,
    ) {
        // Can finalize at any time, even with undecided items (partial finalization)
        val canFinalize: Boolean = !isProcessing
        // Can show "Done" when deletions complete and nothing left to delete
        val canDone: Boolean = deletedCount > 0 && deleteCount == 0 && !isProcessing
    }

    companion object {
        private val TAG = logTag("Swiper", "Status", "ViewModel")
    }
}

package eu.darken.sdmse.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationController @Inject constructor() {
    private var _backStack: NavBackStack<NavKey>? = null

    private val backStack: NavBackStack<NavKey>
        get() = _backStack ?: error("NavigationController not initialized")

    private val resultFlowsLock = Any()
    private val resultFlows = mutableMapOf<String, MutableStateFlow<Any?>>()

    fun setup(backStack: NavBackStack<NavKey>) {
        log(TAG) { "setup()" }
        _backStack = backStack
    }

    fun up(): Boolean {
        // Don't remove the last element to prevent empty backstack
        if (backStack.size <= 1) {
            log(TAG) { "up() prevented removing the last element in backstack" }
            return false
        }
        val removed = backStack.removeLastOrNull()
        log(TAG) { "up() to ${backStack.lastOrNull()} (removed $removed)" }
        return removed != null
    }

    fun goTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(TAG) { "goTo($destination, popUpTo=$popUpTo, inclusive=$inclusive)" }

        if (popUpTo != null) {
            while (backStack.isNotEmpty() && backStack.last() != popUpTo) {
                val removed = backStack.removeLastOrNull()
                log(TAG) { "Popping $removed while looking for $popUpTo" }
            }

            if (inclusive && backStack.isNotEmpty() && backStack.last() == popUpTo) {
                val removed = backStack.removeLastOrNull()
                log(TAG) { "Popping $removed (inclusive)" }
            }
        }

        backStack.add(destination)
    }

    fun replace(destination: NavigationDestination) {
        backStack.removeLastOrNull()
        backStack.add(destination)
    }

    private fun flowFor(name: String): MutableStateFlow<Any?> = synchronized(resultFlowsLock) {
        resultFlows.getOrPut(name) { MutableStateFlow(null) }
    }

    /**
     * Publish a result for cross-screen delivery. Typical usage: a producer screen writes the
     * result before calling [up] so the caller screen's ViewModel can react when it resumes.
     */
    fun <T : Any> setResult(key: ResultKey<T>, value: T) {
        log(TAG) { "setResult(${key.name})" }
        flowFor(key.name).value = value
    }

    /**
     * Observe published results for [key]. Emissions are `T?`; the flow emits `null` both before
     * any result is set and after [consumeResult] clears it, so subscribers that want "value
     * arrived" semantics should apply [kotlinx.coroutines.flow.filterNotNull].
     *
     * See [consumeResults] for the most common shape (filter + auto-clear).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resultFlow(key: ResultKey<T>): Flow<T?> = flowFor(key.name) as Flow<T?>

    /**
     * Clear the current result for [key]. Idempotent; subsequent collectors see `null`.
     */
    fun consumeResult(key: ResultKey<*>) {
        log(TAG) { "consumeResult(${key.name})" }
        flowFor(key.name).value = null
    }

    /**
     * Convenience: a [Flow] that emits each non-null result for [key] and auto-clears the slot
     * as it emits, so the same value is never re-delivered on later recompositions.
     */
    fun <T : Any> consumeResults(key: ResultKey<T>): Flow<T> = resultFlow(key)
        .filterNotNull()
        .onEach { consumeResult(key) }

    companion object {
        private val TAG = logTag("Navigation", "Controller")
    }
}

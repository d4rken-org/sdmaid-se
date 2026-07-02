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
    private var homeRoute: NavKey? = null

    private val backStack: NavBackStack<NavKey>
        get() = _backStack ?: error("NavigationController not initialized")

    private val resultFlowsLock = Any()
    private val resultFlows = mutableMapOf<String, MutableStateFlow<Any?>>()

    // Deep-link / shortcut handling can call goTo() from MainActivity.onResume(), which on a
    // cold start fires before the LaunchedEffect that calls setup(). Queue such calls and drain
    // them once the back stack is wired up, instead of crashing with "not initialized".
    private val pendingActions = mutableListOf<GoToAction>()

    private data class GoToAction(
        val destination: NavigationDestination,
        val popUpTo: NavigationDestination?,
        val inclusive: Boolean,
    )

    fun setup(backStack: NavBackStack<NavKey>, homeRoute: NavKey? = null) {
        log(TAG) { "setup(homeRoute=$homeRoute)" }
        _backStack = backStack
        // Assign unconditionally: this controller is a singleton, a home route from a previous
        // setup() must not leak into a context that didn't supply one (e.g. onboarding).
        this.homeRoute = homeRoute
        if (pendingActions.isNotEmpty()) {
            val drain = pendingActions.toList()
            pendingActions.clear()
            log(TAG) { "Draining ${drain.size} queued navigation action(s)" }
            drain.forEach { goTo(it.destination, it.popUpTo, it.inclusive) }
        }
    }

    fun up(): Boolean {
        // Deep links seed a rootless stack (just the target screen) so the system back gesture
        // exits natively. "Up" from such a sole entry synthesizes the home parent instead of
        // dead-ending — mirroring Android's classic parentActivityName synthetic up-stack.
        val home = homeRoute
        if (backStack.size == 1 && home != null && backStack.last() != home) {
            log(TAG) { "up() from rootless entry ${backStack.last()} → home ($home)" }
            backStack[0] = home
            return true
        }
        // Don't remove the last element to prevent empty backstack
        if (backStack.size <= 1) {
            log(TAG) { "up() prevented removing the last element in backstack" }
            return false
        }
        val removed = backStack.removeLastOrNull()
        log(TAG) { "up() to ${backStack.lastOrNull()} (removed $removed)" }
        return removed != null
    }

    /**
     * Plain app (re)entry (launcher icon, widget open-app tap — no deep-link payload): a live but
     * backgrounded ROOTLESS deep-link stack must not resurface. On Android 12+ backing out of a
     * task root can move the task to the background instead of finishing it, so a leftover
     * [Analyzer]-rooted stack would otherwise greet every future entry. Resets to [homeRoute] when
     * the stack's root isn't home; home-rooted stacks keep normal resume-where-you-left-off
     * semantics, and Recents re-entry (no new intent) still resumes the deep-link session.
     */
    fun resetToHomeOnPlainEntry() {
        val home = homeRoute ?: return
        val stack = _backStack ?: return
        if (stack.isEmpty() || stack.first() == home) return
        log(TAG) { "resetToHomeOnPlainEntry(): clearing rootless deep-link stack ${stack.toList()}" }
        stack[0] = home
        while (stack.size > 1) stack.removeLastOrNull()
    }

    fun goTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(TAG) { "goTo($destination, popUpTo=$popUpTo, inclusive=$inclusive)" }

        if (_backStack == null) {
            log(TAG) { "goTo($destination) queued — controller not initialized yet" }
            pendingActions.add(GoToAction(destination, popUpTo, inclusive))
            return
        }

        // Only drain when the target is actually on the stack. A missing popUpTo target must not
        // empty the back stack — it degrades to a plain push, matching Jetpack NavOptions.popUpTo
        // which no-ops the pop when the destination isn't found.
        if (popUpTo != null && backStack.contains(popUpTo)) {
            while (backStack.isNotEmpty() && backStack.last() != popUpTo) {
                val removed = backStack.removeLastOrNull()
                log(TAG) { "Popping $removed while looking for $popUpTo" }
            }

            if (inclusive && backStack.isNotEmpty() && backStack.last() == popUpTo) {
                val removed = backStack.removeLastOrNull()
                log(TAG) { "Popping $removed (inclusive)" }
            }
        } else if (popUpTo != null) {
            log(TAG) { "popUpTo=$popUpTo not on back stack — pushing $destination without draining" }
        }

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

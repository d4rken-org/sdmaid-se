package eu.darken.sdmse.common.uix

import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.NavigationEventSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Base ViewModel for Compose screens.
 * Provides error events via [SingleEventFlow] and navigation via [NavigationEventSource].
 *
 * Compose render state should be exposed as VM-owned [StateFlow]s. Those render-state flows must
 * stay collector-safe and never throw into `collectAsStateWithLifecycle()`. Use [safeStateIn] to
 * forward recoverable failures to [errorEvents] and emit an explicit fallback UI state instead.
 */
abstract class ViewModel4(
    dispatcherProvider: DispatcherProvider,
    override val tag: String = defaultTag(),
) : ViewModel2(dispatcherProvider, tag), NavigationEventSource {

    val errorEvents = SingleEventFlow<Throwable>()

    override val navEvents = SingleEventFlow<NavEvent>()

    init {
        launchErrorHandler = CoroutineExceptionHandler { _, ex ->
            log(tag) { "Error during launch: ${ex.asLog()}" }
            errorEvents.emitBlocking(ex)
        }
    }

    fun navTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(tag) { "navTo($destination)" }
        navEvents.tryEmit(NavEvent.GoTo(destination, popUpTo, inclusive))
    }

    fun navUp() {
        log(tag) { "navUp()" }
        navEvents.tryEmit(NavEvent.Up)
    }

    /**
     * Collect a render-state flow in [vmScope] and convert upstream failures into explicit fallback
     * UI state plus an [errorEvents] emission. Cancellation is never converted into UI state.
     */
    protected fun <T> Flow<T>.safeStateIn(
        initialValue: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(5000),
        onError: (Throwable) -> T,
    ): StateFlow<T> = this
        .catch { ex ->
            if (ex is CancellationException) throw ex

            log(tag, WARN) { "Error during state collection: ${ex.asLog()}" }
            errorEvents.emit(ex)
            emit(onError(ex))
        }
        .stateIn(
            scope = vmScope,
            started = started,
            initialValue = initialValue,
        )

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM4"
    }
}

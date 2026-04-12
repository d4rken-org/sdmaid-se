package eu.darken.sdmse.common.uix

import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.NavigationEventSource
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Base ViewModel for Compose screens.
 * Extends ViewModel2 directly — sibling to the deprecated ViewModel3.
 * Provides error events via [SingleEventFlow] and navigation via [NavigationEventSource].
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

    companion object {
        private fun defaultTag(): String = this::class.simpleName ?: "VM4"
    }
}

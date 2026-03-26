package eu.darken.sdmse.common.uix

import androidx.navigation.NavOptions
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.error.ErrorEventSource
import eu.darken.sdmse.common.navigation.NavCommand
import eu.darken.sdmse.common.navigation.NavEventSource


abstract class ViewModel3(
    dispatcherProvider: DispatcherProvider,
) : ViewModel2(dispatcherProvider), NavEventSource, ErrorEventSource {

    override val navEvents = SingleLiveEvent<NavCommand?>()
    override val errorEvents = SingleLiveEvent<Throwable>()

    fun navigateTo(route: Any, navOptions: NavOptions? = null) {
        navEvents.postValue(NavCommand.To(route, navOptions))
    }

    fun popNavStack() {
        navEvents.postValue(NavCommand.Back)
    }
}

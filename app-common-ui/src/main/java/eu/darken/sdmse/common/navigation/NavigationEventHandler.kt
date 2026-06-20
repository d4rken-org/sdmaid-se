package eu.darken.sdmse.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

@Composable
fun NavigationEventHandler(vararg sources: NavigationEventSource) {
    val navController = LocalNavigationController.current
    if (navController == null) {
        log(TAG, WARN) { "NavigationController is unavailable" }
        return
    }
    sources.forEach { source ->
        val navEvents = source.navEvents
        LaunchedEffect(navEvents) {
            navEvents.collect { event ->
                when (event) {
                    is NavEvent.GoTo -> navController.goTo(
                        destination = event.destination,
                        popUpTo = event.popUpTo,
                        inclusive = event.inclusive,
                    )

                    is NavEvent.Up -> navController.up()
                }
            }
        }
    }
}

private val TAG = logTag("Navigation", "EventHandler")

package eu.darken.sdmse.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

/**
 * Bridges ViewModel3's [SingleLiveEvent]<[NavCommand]?> navigation to the Compose [NavigationController].
 * Used for LiveData-bridged screens that still use ViewModel3.
 */
// FIXME: Remove after Compose rewrite — migrate ViewModel3 subclasses to ViewModel4
@Deprecated("Use NavigationEventHandler with ViewModel4 instead")
@Composable
fun LegacyNavigationBridge(navEvents: SingleLiveEvent<NavCommand?>) {
    val navController = LocalNavigationController.current
    if (navController == null) {
        log(TAG, WARN) { "NavigationController is unavailable" }
        return
    }

    @Suppress("DEPRECATION")
    val navCommand = navEvents.observeAsState()
    val command = navCommand.value ?: return

    LaunchedEffect(command) {
        when (command) {
            is NavCommand.To -> {
                val destination = command.route as? NavigationDestination
                if (destination != null) {
                    navController.goTo(destination)
                } else {
                    log(TAG, WARN) { "NavCommand.To route is not a NavigationDestination: ${command.route}" }
                }
            }

            is NavCommand.Back -> navController.up()
        }
    }
}

private val TAG = logTag("Navigation", "LegacyBridge")

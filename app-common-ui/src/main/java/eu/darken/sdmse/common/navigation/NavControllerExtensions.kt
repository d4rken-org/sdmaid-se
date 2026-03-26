package eu.darken.sdmse.common.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

private val TAG = logTag("Navigation")

fun NavController.safeNavigate(route: Any, navOptions: NavOptions? = null) {
    try {
        navigate(route, navOptions)
    } catch (e: IllegalArgumentException) {
        log(TAG, WARN) { "Navigation dropped: route=$route, error=${e.message}" }
        if (Bugs.isDebug) throw e
    }
}

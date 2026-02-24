package eu.darken.sdmse.common.navigation

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

private val TAG = logTag("Navigation")

fun NavController.navigateIfNotThere(@IdRes resId: Int, args: Bundle? = null) {
    if (currentDestination?.id == resId) return
    navigate(resId, args)
}

fun NavController.doNavigate(direction: NavDirections) {
    val curDest = currentDestination
    val action = curDest?.getAction(direction.actionId)
    if (action != null) {
        navigate(direction)
        return
    }
    log(TAG, WARN) {
        "Navigation dropped:" +
            " action=${direction.javaClass.simpleName}" +
            " (0x${Integer.toHexString(direction.actionId)})" +
            " curDest=${curDest?.label ?: "null"}" +
            " (${curDest?.id?.let { "0x${Integer.toHexString(it)}" } ?: "null"})" +
            " graphSet=${isGraphSet()}"
    }
}

fun NavController.isGraphSet(): Boolean = try {
    graph
    true
} catch (e: IllegalStateException) {
    false
}
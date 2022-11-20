package eu.darken.sdmse.common.navigation

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections

fun NavController.navigateIfNotThere(@IdRes resId: Int, args: Bundle? = null) {
    if (currentDestination?.id == resId) return
    navigate(resId, args)
}

fun NavController.doNavigate(direction: NavDirections) {
    currentDestination?.getAction(direction.actionId)?.let { navigate(direction) }
}

fun NavController.isGraphSet(): Boolean = try {
    graph
    true
} catch (e: IllegalStateException) {
    false
}
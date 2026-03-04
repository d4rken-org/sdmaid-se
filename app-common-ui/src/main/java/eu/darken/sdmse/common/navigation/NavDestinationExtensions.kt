package eu.darken.sdmse.common.navigation

import androidx.annotation.IdRes
import androidx.navigation.NavDestination

fun NavDestination?.hasAction(@IdRes id: Int): Boolean {
    if (this == null) return false
    return getAction(id) != null
}
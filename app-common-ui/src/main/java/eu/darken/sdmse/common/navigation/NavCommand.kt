package eu.darken.sdmse.common.navigation

import androidx.navigation.NavOptions

sealed class NavCommand {
    data class To(val route: Any, val navOptions: NavOptions? = null) : NavCommand()
    data object Back : NavCommand()
}

package eu.darken.sdmse.common.navigation

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import androidx.navigation.NavArgsLazy
import androidx.navigation.NavDirections
import java.io.Serializable

// TODO Remove with "androidx.navigation:navigation-safe-args-gradle-plugin:2.4.0-alpha/stable"
inline fun <reified Args : NavArgs> SavedStateHandle.navArgs() = NavArgsLazy(Args::class) {
    Bundle().apply {
        keys().forEach {
            when (val value = get<Any>(it)) {
                is Serializable -> putSerializable(it, value)
                is Parcelable -> putParcelable(it, value)
            }
        }
    }
}

fun navDirections(@IdRes actionId: Int, args: Bundle = Bundle.EMPTY): NavDirections = object : NavDirections {
    override val actionId: Int = actionId
    override val arguments: Bundle = args
}
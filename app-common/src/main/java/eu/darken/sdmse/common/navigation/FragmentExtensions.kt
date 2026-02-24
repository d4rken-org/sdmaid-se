package eu.darken.sdmse.common.navigation

import android.app.Activity
import android.content.res.Configuration
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.annotation.PluralsRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.getCompatColor
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.getSpanCount

fun Fragment.doNavigate(direction: NavDirections) {
    if (!isAdded) {
        log(WARN) { "Trying to navigate from ${this.javaClass.simpleName} that isn't added: ${direction.javaClass.simpleName}" }
        return
    }
    findNavController().doNavigate(direction)
}

fun Fragment.popBackStack(): Boolean {
    if (!isAdded) {
        IllegalStateException("Fragment is not added").also {
            log(WARN) { "Trying to pop backstack on Fragment that isn't added to an Activity: ${it.asLog()}" }
        }
        return false
    }
    return findNavController().popBackStack()
}

/**
 * [FragmentContainerView] does not access [NavController] in [Activity.onCreate]
 * as workaround [FragmentManager] is used to get the [NavController]
 * @param id [Int] NavFragment id
 * @see <a href="https://issuetracker.google.com/issues/142847973">issue-142847973</a>
 */
@Throws(IllegalStateException::class)
fun FragmentManager.findNavController(@IdRes id: Int): NavController {
    val fragment = findFragmentById(id) ?: throw IllegalStateException("Fragment is not found for id:$id")
    return NavHostFragment.findNavController(fragment)
}

fun Fragment.getQuantityString2(
    @PluralsRes pluralRes: Int,
    quantity: Int,
) = requireContext().getQuantityString2(pluralRes, quantity)

fun Fragment.getQuantityString2(
    @PluralsRes stringRes: Int,
    quantity: Int,
    vararg formatArgs: Any
) = requireContext().getQuantityString2(stringRes, quantity, *formatArgs)

fun Fragment.isTablet(): Boolean {
    return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
}

fun Fragment.getSpanCount(widthDp: Int = 410) = requireContext().getSpanCount(widthDp = widthDp)

fun Fragment.isLandscape(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@ColorInt
fun Fragment.getColorForAttr(@AttrRes attrId: Int): Int = requireContext().getColorForAttr(attrId)

@ColorInt
fun Fragment.getCompatColor(@ColorRes attrId: Int): Int = requireContext().getCompatColor(attrId)

package eu.darken.sdmse.common.ui

import android.view.View
import com.google.android.material.appbar.AppBarLayout
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log

fun AppBarLayout.shouldLift(view: View): Boolean = try {
    val findLiftMethod = AppBarLayout::class.java.getDeclaredMethod("shouldLift", View::class.java).apply {
        isAccessible = true
    }
    findLiftMethod.invoke(this, view) as Boolean
} catch (e: Exception) {
    log(ERROR) { "Failed reflection call on shouldLift($view): ${e.asLog()}" }
    false
}

fun AppBarLayout.updateLiftStatus(view: View) {
    log(VERBOSE) { "updateLiftStatus($view)" }
    post {
        liftOnScrollTargetViewId = view.id
        isLifted = shouldLift(view).also {
            log(VERBOSE) { "shouldLift($view): $it" }
        }
    }
}
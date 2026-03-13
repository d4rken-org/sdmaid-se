package eu.darken.sdmse.common

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag


class EdgeToEdgeHelper(activity: Activity) {

    private val tag = logTag("EdgeToEdge", "$activity")

    fun insetsPadding(
        view: View,
        left: Boolean = false,
        top: Boolean = false,
        right: Boolean = false,
        bottom: Boolean = false,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, insets: WindowInsetsCompat ->
            if (Bugs.isTrace) log(tag, VERBOSE) { "Applying padding insets to $v" }
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout: Insets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                if (left) maxOf(systemBars.left, displayCutout.left) else v.paddingLeft,
                if (top) maxOf(systemBars.top, displayCutout.top) else v.paddingTop,
                if (right) maxOf(systemBars.right, displayCutout.right) else v.paddingRight,
                if (bottom) maxOf(systemBars.bottom, displayCutout.bottom) else v.paddingBottom,
            )
            insets
        }
    }
}

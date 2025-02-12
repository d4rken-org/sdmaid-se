package eu.darken.sdmse.common

import android.app.Activity
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
            log(tag) { "Applying padding insets to $v" }
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                if (left) systemBars.left else v.paddingLeft,
                if (top) systemBars.top else v.paddingTop,
                if (right) systemBars.right else v.paddingRight,
                if (bottom) systemBars.bottom else v.paddingBottom,
            )
            insets
        }
    }
}

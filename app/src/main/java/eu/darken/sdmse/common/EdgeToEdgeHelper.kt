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
        left: Float? = 0f,
        top: Float? = 0f,
        right: Float? = 0f,
        bottom: Float? = 0f,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, insets: WindowInsetsCompat ->
            val ctx = v.context
            log(tag) { "Applying padding insets to $v" }
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                if (left == null) v.paddingLeft else systemBars.left + ctx.dpToPx(left),
                if (top == null) v.paddingTop else systemBars.top + ctx.dpToPx(top),
                if (right == null) v.paddingRight else systemBars.right + ctx.dpToPx(right),
                if (bottom == null) v.paddingBottom else systemBars.bottom + ctx.dpToPx(bottom),
            )
            insets
        }
    }
}

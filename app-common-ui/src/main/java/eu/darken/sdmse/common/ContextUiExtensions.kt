package eu.darken.sdmse.common

import android.content.Context
import android.content.res.TypedArray
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.debug.logging.log
import kotlin.math.max


@ColorInt
fun Context.getColorForAttr(@AttrRes attrId: Int): Int {
    var typedArray: TypedArray? = null
    try {
        typedArray = this.theme.obtainStyledAttributes(intArrayOf(attrId))
        return typedArray.getColor(0, 0)
    } finally {
        typedArray?.recycle()
    }
}

@ColorInt
fun Context.getCompatColor(@ColorRes attrId: Int): Int {
    return ContextCompat.getColor(this, attrId)
}

fun Context.dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}

fun Context.spToPx(sp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
}

fun Context.getSpanCount(widthDp: Int = 390): Int {
    val displayMetrics = resources.displayMetrics
    val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
    val count = (screenWidthDp / widthDp + 0.5).toInt()
    return max(count, 1).also {
        log { "getSpanCount($screenWidthDp/$widthDp)=$it" }
    }
}

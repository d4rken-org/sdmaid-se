package eu.darken.sdmse.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.TypedArray
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment


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
fun Fragment.getColorForAttr(@AttrRes attrId: Int): Int = requireContext().getColorForAttr(attrId)

@ColorInt
fun Context.getCompatColor(@ColorRes attrId: Int): Int {
    return ContextCompat.getColor(this, attrId)
}

@ColorInt
fun Fragment.getCompatColor(@ColorRes attrId: Int): Int = requireContext().getCompatColor(attrId)

@SuppressLint("NewApi")
fun Context.startServiceCompat(intent: Intent) {
    if (hasApiLevel(26)) startForegroundService(intent) else startService(intent)
}

fun Context.dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
}

fun Context.spToPx(sp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
}

fun Context.getQuantityString2(@StringRes stringRes: Int, quantity: Int) = resources.getQuantityString(
    stringRes,
    quantity,
    quantity
)

package eu.darken.sdmse.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.TypedValue
import androidx.annotation.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import okio.Source
import okio.source


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

fun Context.getQuantityString2(
    @PluralsRes stringRes: Int,
    quantity: Int
) = resources.getQuantityString(stringRes, quantity, quantity)

fun Context.getQuantityString2(
    @PluralsRes stringRes: Int,
    quantity: Int,
    vararg formatArgs: Any
) = resources.getQuantityString(stringRes, quantity, *formatArgs)

fun Context.openAsset(path: String): Source {
    return assets.open(path).source()
}

fun Context.getColorStateListFor(@ColorRes colorRes: Int): ColorStateList {
    return ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
}
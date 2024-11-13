package eu.darken.sdmse.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.debug.logging.log
import okio.Source
import okio.source
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

fun Context.isInstalled(pkgName: String) = try {
    @Suppress("DEPRECATION")
    this.packageManager.getPackageInfo(pkgName, 0) != null
} catch (_: PackageManager.NameNotFoundException) {
    false
}

fun Context.getSpanCount(widthDp: Int = 390): Int {
    val displayMetrics = resources.displayMetrics
    val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
    val count = (screenWidthDp / widthDp + 0.5).toInt()
    return max(count, 1).also {
        log { "getSpanCount($screenWidthDp/$widthDp)=$it" }
    }
}

fun Context.getPackageInfo(): PackageInfo = packageManager.getPackageInfo(packageName, 0)
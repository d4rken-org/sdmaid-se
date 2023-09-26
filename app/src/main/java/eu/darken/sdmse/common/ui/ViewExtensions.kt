package eu.darken.sdmse.common.ui

import android.content.res.TypedArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleableRes
import eu.darken.sdmse.R

fun ImageView.updateExpander(dependency: View) {
    val toggleRes = if (dependency.visibility == View.VISIBLE) {
        R.drawable.ic_expand_less
    } else {
        R.drawable.ic_expand_more
    }
    setImageResource(toggleRes)
}

fun TypedArray.getStringOrRef(@StyleableRes styleRes: Int): String? {
    if (!hasValue(styleRes)) return null

    val stringId = getResourceId(styleRes, 0)
    return if (stringId != 0) {
        resources.getString(stringId)

    } else {
        getNonResourceString(styleRes)
    }
}

@DrawableRes
fun TypedArray.getDrawableRes(@StyleableRes styleRes: Int): Int? {
    if (!hasValue(styleRes)) return null

    return getResourceId(styleRes, 0)
}

fun View.getString(@StringRes stringRes: Int) = resources.getString(stringRes)

val View.layoutInflator: LayoutInflater
    get() = LayoutInflater.from(context)

fun View.performClickWithRipple() {
    isPressed = true
    isPressed = false
    performClick()
}

fun View.setEnabledStateRecursive(enabled: Boolean) {
    this.isEnabled = enabled
    if (this !is ViewGroup) return

    for (i in childCount - 1 downTo 0) {
        getChildAt(i).setEnabledStateRecursive(enabled)
    }
}
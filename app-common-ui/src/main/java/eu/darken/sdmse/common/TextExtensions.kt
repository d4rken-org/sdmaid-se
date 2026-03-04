package eu.darken.sdmse.common

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt

fun colorString(@ColorInt color: Int, string: String): SpannableString {
    val colored = SpannableString(string)
    colored.setSpan(ForegroundColorSpan(color), 0, string.length, 0)
    return colored
}
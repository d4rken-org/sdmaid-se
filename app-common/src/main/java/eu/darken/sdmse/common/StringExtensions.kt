package eu.darken.sdmse.common

import android.content.Context
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import java.util.*

val rngString
    get() = UUID.randomUUID().toString()


fun String?.hashCode(ignoreCase: Boolean): Int {
    if (this == null) return 0
    return if (ignoreCase) lowercase().hashCode() else hashCode()
}


fun List<String>?.hashCode(ignoreCase: Boolean): Int {
    if (this == null) return 0
    return if (ignoreCase) this.map { it.lowercase() }.hashCode() else hashCode()
}

fun String.toColored(
    context: Context,
    @ColorRes colorRes: Int,
): SpannableString = SpannableString(this).apply {
    setSpan(ForegroundColorSpan(ContextCompat.getColor(context, colorRes)), 0, this.length, 0)
}
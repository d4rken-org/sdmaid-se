package eu.darken.sdmse.common.ui

import android.content.res.ColorStateList
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import eu.darken.sdmse.common.getColorForAttr

fun MaterialTextView.setLeftIcon(
    @DrawableRes iconRes: Int,
    @AttrRes tintRes: Int? = null,
) {
    setCompoundDrawablesRelativeWithIntrinsicBounds(
        ContextCompat.getDrawable(context, iconRes),
        null,
        null,
        null,
    )
    if (tintRes != null) {
        TextViewCompat.setCompoundDrawableTintList(
            this,
            ColorStateList.valueOf(context.getColorForAttr(tintRes))
        )
    }
}
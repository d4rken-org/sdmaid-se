package eu.darken.sdmse.common.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.ListPreference


class ListPreference2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    @StyleRes defStyleRes: Int = 0,
) : ListPreference(context, attrs, defStyleAttr, defStyleRes) {

    var alternativeClickListener: ((ListPreference2) -> Unit)? = null

    override fun onClick() {
        if (alternativeClickListener != null) {
            alternativeClickListener?.invoke(this)
        } else {
            super.onClick()
        }
    }
}
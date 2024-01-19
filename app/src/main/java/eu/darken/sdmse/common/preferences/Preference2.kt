package eu.darken.sdmse.common.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.darken.sdmse.common.debug.logging.logTag


open class Preference2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var longClickListener: View.OnLongClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.setOnLongClickListener(longClickListener)
    }

    fun setOnLongClickListener(action: View.OnLongClickListener?) {
        longClickListener = action
    }

    companion object {
        private val TAG = logTag("Preference2")
    }
}
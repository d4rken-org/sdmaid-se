package eu.darken.sdmse.common.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.children
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.MimeTypes.Json.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.databinding.ViewCaveatPreferenceViewBinding


class CaveatPreferenceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = 0,
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.view_caveat_preference_view
        isVisible = false
        isPersistent = false
    }

    var caveatMessage: String? = summary?.toString()
        set(value) {
            field = value
            notifyChanged()
        }

    var caveatAction: String? = title?.toString()
        set(value) {
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        ViewCaveatPreferenceViewBinding.bind(holder.itemView).apply {
            primary.text = caveatMessage
            positiveAction.text = caveatAction
            positiveAction.setOnClickListener {
                @Suppress("RestrictedApi")
                this@CaveatPreferenceView.performClick()
            }
            (holder.itemView as ViewGroup).children
        }
    }

    companion object {
        private val TAG = logTag("CaveatPreferenceView")
    }
}
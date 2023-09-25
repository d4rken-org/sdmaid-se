package eu.darken.sdmse.common.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceViewHolder
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.databinding.ViewPreferenceCheckboxBadgeOverlayBinding


class BadgedCheckboxPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.checkBoxPreferenceStyle,
    @StyleRes defStyleRes: Int = 0,
) : CheckBoxPreference(context, attrs, defStyleAttr, defStyleRes) {

    var badgedAction: (() -> Unit)? = null

    var isRestricted: Boolean = true
        set(value) {
            field = value
            isEnabled = !value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val ogParent = holder.itemView as LinearLayout

        val newParent = LinearLayout(context).apply {
            minimumHeight = ogParent.minimumHeight
            ogParent.minimumHeight = 0

            gravity = ogParent.gravity

            setPadding(ogParent.paddingLeft, ogParent.paddingTop, ogParent.paddingRight, ogParent.paddingBottom)
            ogParent.setPadding(0, 0, 0, 0)

            background = ogParent.background
            ogParent.background = null

            ogParent.children.toList()
                .onEach { ogParent.removeView(it) }
                .forEach { addView(it) }
        }

        val grandparent = FrameLayout(context).apply {
            addView(newParent)
        }

        ViewPreferenceCheckboxBadgeOverlayBinding.inflate(LayoutInflater.from(context), grandparent, true).apply {
            root.isVisible = !isEnabled
            root.setOnClickListener { badgedAction?.invoke() }
        }

        ogParent.apply {
            addView(grandparent)
        }

    }

    companion object {
        private val TAG = logTag("BadgedCheckboxPreference")
    }
}
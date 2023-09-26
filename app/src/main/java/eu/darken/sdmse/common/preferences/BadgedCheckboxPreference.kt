package eu.darken.sdmse.common.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceViewHolder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ui.performClickWithRipple
import eu.darken.sdmse.common.ui.setEnabledStateRecursive
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
            isEnabled = !isRestricted
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val ogParent = holder.itemView as LinearLayout

        val overlay = if (ogParent.findViewById<View>(R.id.badge_overlay) == null) {
            val ogChildren = ogParent.children.toList()
            ogChildren.onEach { ogParent.removeView(it) }

            // We also copy the ogParents attributes to the new one and reset them
            // We move all child views from the original parent to this one
            val newParent = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = ogParent.minimumHeight
                ogParent.minimumHeight = 0

                gravity = ogParent.gravity

                setPadding(ogParent.paddingLeft, ogParent.paddingTop, ogParent.paddingRight, ogParent.paddingBottom)
                ogParent.setPadding(0, 0, 0, 0)

                background = ogParent.background
                ogParent.background = null

                ogChildren.forEach { addView(it) }
            }

            // This will hold both our overlay and the new parent containing all the og children
            val container = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                addView(newParent)
                ogParent.addView(this)
            }

            ViewPreferenceCheckboxBadgeOverlayBinding.inflate(LayoutInflater.from(context), container, true)
        } else {
            ViewPreferenceCheckboxBadgeOverlayBinding.bind(ogParent.findViewById(R.id.badge_overlay))
        }

        val underlayView = (ogParent.children.single() as ViewGroup).children.filter { it != overlay.root }.single()

        underlayView.setEnabledStateRecursive(!isRestricted)

        overlay.apply {
            // Otherwise normal isEnabled behavior disables all overlay elements
            root.setEnabledStateRecursive(true)
            root.isInvisible = !isRestricted
            root.setOnClickListener {
                badgedAction?.invoke()
                badgeButton.performClickWithRipple()
            }
        }
    }

    companion object {
        private val TAG = logTag("BadgedCheckboxPreference")
    }
}
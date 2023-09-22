package eu.darken.sdmse.common.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.PreferenceGroup
import androidx.preference.children
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.logTag


class CaveatPreferenceGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = 0,
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.view_caveat_preference_group
        isPersistent = false
    }

    private val caveatEntry: CaveatPreferenceView
        get() = children.filterIsInstance<CaveatPreferenceView>().first()

    var caveatMessage: String? = null
        set(value) {
            field = value
            caveatEntry.caveatMessage = value
        }

    var caveatAction: String? = null
        set(value) {
            field = value
            caveatEntry.caveatAction = value
        }

    var showCaveat: Boolean = false
        set(value) {
            field = value
            caveatEntry.isVisible = value
        }

    var caveatClickListener: (() -> Boolean)? = null
        set(value) {
            field = value
            caveatEntry.apply {
                if (value != null) {
                    this.setOnPreferenceClickListener { value.invoke() }
                } else {
                    this.onPreferenceClickListener = null
                }
            }
        }

    companion object {
        private val TAG = logTag("CaveatPreferenceGroup")
    }
}
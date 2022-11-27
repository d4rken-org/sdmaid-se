package eu.darken.sdmse.common.ui

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import eu.darken.sdmse.R
import eu.darken.sdmse.databinding.ViewPreferenceBinding


open class PreferenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val ui = ViewPreferenceBinding.inflate(layoutInflator, this)

    init {
        lateinit var typedArray: TypedArray
        try {
            typedArray = getContext().theme.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = typedArray.getDrawable(0)
        } finally {
            typedArray.recycle()
        }

        try {
            typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PreferenceView)

            val iconRes = typedArray.getResourceId(R.styleable.PreferenceView_pvIcon, 0)
            if (iconRes != 0) ui.icon.setImageResource(iconRes)
            else ui.icon.visibility = View.GONE

            val titleRes = typedArray.getResourceId(R.styleable.PreferenceView_pvTitle, 0)
            if (titleRes != 0) {
                ui.title.setText(titleRes)
            } else {
                ui.title.text = typedArray.getNonResourceString(R.styleable.PreferenceView_pvTitle)
            }

            if (typedArray.hasValue(R.styleable.PreferenceView_pvDescription)) {
                val descId = typedArray.getResourceId(R.styleable.PreferenceView_pvDescription, 0)
                if (descId != 0) {
                    ui.description.setText(descId)
                } else {
                    ui.description.text = typedArray.getNonResourceString(R.styleable.PreferenceView_pvDescription)
                }
            }
            ui.description.setGone(ui.description.text.isNullOrEmpty())
        } finally {
            typedArray.recycle()
        }
    }

    fun addExtra(view: View?) {
        ui.extra.apply {
            addView(view)
            visibility = if (view != null) View.VISIBLE else View.GONE
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        setEnabledRecursion(this, enabled)
    }

    private fun setEnabledRecursion(vg: ViewGroup, enabled: Boolean) {
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)
            if (child is ViewGroup) setEnabledRecursion(child, enabled)
            else child.isEnabled = enabled
        }
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        ui.icon.setImageResource(iconRes)
    }

    var description: String
        get() = ui.description.text.toString()
        set(value) {
            ui.description.apply {
                text = value
                setGone(value.isEmpty())
            }
        }
}

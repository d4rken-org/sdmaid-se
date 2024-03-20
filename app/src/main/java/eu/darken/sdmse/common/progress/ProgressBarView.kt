package eu.darken.sdmse.common.progress

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.progress.Progress.Count
import eu.darken.sdmse.common.ui.layoutInflator
import eu.darken.sdmse.databinding.ViewProgressbarBinding

class ProgressBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = ViewProgressbarBinding.inflate(layoutInflator, this)

    fun setProgress(data: Progress.Data) {
        ui.icon.apply {
            setImageDrawable(data.icon?.get(context))
            isGone = data.icon == null
        }
        ui.primary.apply {
            val newText = data.primary.get(context)
            text = newText
            isInvisible = newText.isEmpty()
        }
        ui.secondary.apply {
            val newText = data.secondary.get(context)
            text = newText
            isInvisible = newText.isEmpty()
        }

        ui.progress.apply {
            isGone = data.count is Count.None
            when (data.count) {
                is Count.Counter -> {
                    isIndeterminate = false
                    trackColor = context.getColorForAttr(com.google.android.material.R.attr.colorPrimaryContainer)
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Count.Percent -> {
                    isIndeterminate = false
                    trackColor = context.getColorForAttr(com.google.android.material.R.attr.colorPrimaryContainer)
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Count.Indeterminate -> {
                    isIndeterminate = true
                    trackColor = Color.TRANSPARENT
                }
                is Count.Size -> {}
                is Count.None -> {}
            }
        }
        ui.progressText.apply {
            text = data.count.displayValue(context)
            isInvisible = data.count is Count.Indeterminate || data.count is Count.None
        }
    }

}
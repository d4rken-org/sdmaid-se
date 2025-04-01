package eu.darken.sdmse.automation.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.Progress.Count
import eu.darken.sdmse.common.ui.layoutInflator
import eu.darken.sdmse.databinding.AutomationControlViewBinding

class AutomationControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = AutomationControlViewBinding.inflate(layoutInflator, this)

    fun setProgress(data: Progress.Data?) {
        log(VERBOSE) { "setProgress($data)" }
        isVisible = data != null

        if (data == null) {
            ui.clickScreenMascotContainer.pauseAnimation()
            return
        }

        ui.clickScreenMascotContainer.apply {
            if (!isAnimating) playAnimation()
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

    fun setTitle(title: CaString, subtitle: CaString) {
        ui.title.text = title.get(context)
        ui.subtitle.text = subtitle.get(context)
    }

    fun showVeil(show: Boolean) {
        ui.clickScreen.isVisible = show
        ui.clickScreenExplanation.isVisible = show
        ui.clickScreenMascotContainer.isVisible = show
    }

    fun setCancelListener(listener: OnClickListener?) {
        ui.cancelAction.setOnClickListener(listener)
    }
}
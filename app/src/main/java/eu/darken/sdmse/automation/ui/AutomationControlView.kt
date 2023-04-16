package eu.darken.sdmse.automation.ui

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
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
    private val wiggleAnim = AnimationUtils.loadAnimation(context, R.anim.anim_wiggle)

    private var clickCount = 0

    fun setProgress(data: Progress.Data?) {
        isVisible = data != null

        if (data == null) {
            ui.mascotAnimated.pauseAnimation()
            return
        }

        ui.mascotAnimated.apply {
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
                    isIndeterminate = data.count.current == 0L
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Count.Percent -> {
                    isIndeterminate = data.count.current == 0L
                    progress = data.count.current.toInt()
                    max = data.count.max.toInt()
                }
                is Count.Indeterminate -> {
                    isIndeterminate = true
                }
                is Count.Size -> {}
                is Count.None -> {}
            }
        }
        ui.progressText.apply {
            text = data.count.displayValue(context)
            isInvisible = data.count is Count.Indeterminate || data.count is Count.None || data.count.current == 0L
        }
    }

    fun setTitle(title: CaString, subtitle: CaString) {
        ui.title.text = title.get(context)
        ui.subtitle.text = subtitle.get(context)
    }

    fun showOverlay(show: Boolean) {
        ui.clickScreen.isVisible = show
        ui.clickScreenExplanation.isVisible = show
        ui.clickScreenMascotContainer.isVisible = show
    }

    fun setCancelListener(listener: OnClickListener?) {
        ui.cancelAction.setOnClickListener(listener)
        ui.clickScreenMascotContainer.apply {
            setOnClickListener {
                clickCount++
                if (clickCount % 5 == 0) startAnimation(wiggleAnim)
            }
        }
    }
}